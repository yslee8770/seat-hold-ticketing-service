package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateRequest;
import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.EventStatus;
import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldGroupSeat;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.HoldIdempotency;
import com.example.ticket.domain.idempotency.SeatIdsCodec;
import com.example.ticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final Clock clock;
    private final HoldIdempotencyRepository holdIdempotencyRepository;
    private final HoldGroupRepository holdGroupRepository;
    private final HoldGroupSeatRepository holdGroupSeatRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;

    @Transactional
    public HoldCreateResponse hold(long userId, long eventId, HoldCreateRequest request, String idempotencyKey) {
        Instant now = HoldTimes.now(clock);
        Instant expiresAt = HoldTimes.holdUntil(clock);

        validEvent(eventId, now);

        List<Long> seatIds = getNormalizedSeatIds(request.seatIds());
        checkHoldSeatsCount(userId, eventId, now, seatIds.size());

        String seatIdsKey = SeatIdsCodec.toCanonicalString(seatIds);

        HoldIdempotency idem = resolveOrCreateIdempotency(userId, eventId, idempotencyKey, seatIdsKey, now, expiresAt);

        if (idem.isCompleted()) {
            return HoldCreateResponse.from(idem);
        }

        HoldGroup holdGroup = holdGroupRepository.save(HoldGroup.create(userId, expiresAt, eventId));
        int saved = createHoldSeats(eventId, seatIds, expiresAt, holdGroup);

        idem.holdComplete(holdGroup.getId(), saved);
        holdIdempotencyRepository.save(idem);

        return HoldCreateResponse.from(idem);
    }


    @Transactional(readOnly = true)
    public void validEvent(long eventId, Instant now) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getStatus().equals(EventStatus.OPEN) || event.getSalesOpenAt().isAfter(now) || !now.isBefore(event.getSalesCloseAt())) {
            throw new BusinessRuleViolationException(ErrorCode.EVENT_NOT_ON_SALE);
        }
    }

    @Transactional(readOnly = true)
    public void checkHoldSeatsCount(long userId, long eventId, Instant now, long requestSeatIdsCount) {
        List<Long> holdGroupIds = holdGroupRepository.findActiveIds(userId, eventId, now);
        if (!holdGroupIds.isEmpty()) {
            long holdSeatsCount = holdGroupSeatRepository.countByEventIdAndHoldGroupIdInAndExpiresAtAfter(eventId, holdGroupIds, now);
            if (holdSeatsCount + requestSeatIdsCount > 4) {
                throw new BusinessRuleViolationException(ErrorCode.HOLD_LIMIT_EXCEEDED);
            }
        } else {
            if (requestSeatIdsCount > 4) {
                throw new BusinessRuleViolationException(ErrorCode.HOLD_LIMIT_EXCEEDED);
            }
        }
    }

    @Transactional
    protected HoldIdempotency createHoldIdempotency(
            long userId,
            long eventId,
            String idempotencyKey,
            String seatIdsKey,
            Instant expiresAt,
            Instant now
    ) {
        try {
            return holdIdempotencyRepository.save(
                    HoldIdempotency.create(userId, idempotencyKey, eventId, seatIdsKey, expiresAt)
            );
        } catch (DataIntegrityViolationException e) {
            if (!isConstraint(e, "uk_hold_idempotency_user_event_key")) {
                throw e;
            }

            HoldIdempotency existing = holdIdempotencyRepository
                    .findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey)
                    .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT));

            if (!existing.getSeatIdsKey().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }

            if (existing.isCompleted()) {
                return existing;
            }

            if (existing.getExpiresAt().isAfter(now)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }

            int deleted = holdIdempotencyRepository.deleteStaleInProgress(
                    userId, eventId, idempotencyKey, now
            );
            if (deleted == 1) {
                try {
                    return holdIdempotencyRepository.save(
                            HoldIdempotency.create(userId, idempotencyKey, eventId, seatIdsKey, expiresAt)
                    );
                } catch (DataIntegrityViolationException e2) {
                    if (!isConstraint(e2, "uk_hold_idempotency_user_event_key")) {
                        throw e2;
                    }

                    HoldIdempotency reread = holdIdempotencyRepository
                            .findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey)
                            .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT));

                    if (!reread.getSeatIdsKey().equals(seatIdsKey)) {
                        throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
                    }
                    if (reread.isCompleted()) {
                        return reread;
                    }
                    throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
            }
            HoldIdempotency reread = holdIdempotencyRepository
                    .findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey)
                    .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT));

            if (!reread.getSeatIdsKey().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (reread.isCompleted()) {
                return reread;
            }
            throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
    }


    @Transactional
    public int createHoldSeats(long eventId, List<Long> seatIds, Instant expiresAt, HoldGroup holdGroup) {
        try {
            long seatsSize = seatRepository.countByEventIdAndIdInAndStatus(eventId, seatIds, SeatStatus.AVAILABLE);
            if (seatsSize != seatIds.size()) {
                throw new BusinessRuleViolationException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            List<HoldGroupSeat> savedHoldGroupSeats = holdGroupSeatRepository.saveAll(
                    seatIds.stream()
                            .map(seatId -> HoldGroupSeat.create(seatId, eventId, expiresAt, holdGroup.getId()))
                            .toList());
            return savedHoldGroupSeats.size();
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uk_hold_group_seats_seat_id_event_id")) {
                throw new BusinessRuleViolationException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            throw e;
        }
    }

    @Transactional
    protected HoldIdempotency resolveOrCreateIdempotency(
            long userId,
            long eventId,
            String idempotencyKey,
            String seatIdsKey,
            Instant now,
            Instant expiresAt
    ) {
        Optional<HoldIdempotency> found =
                holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey);

        if (found.isPresent()) {
            HoldIdempotency existing = found.get();

            if (!existing.getSeatIdsKey().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }

            if (existing.isCompleted()) {
                return existing;
            }

            if (existing.getExpiresAt().isAfter(now)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }

            int deleted = holdIdempotencyRepository.deleteStaleInProgress(
                    userId, eventId, idempotencyKey, now
            );

            if (deleted == 0) {
                HoldIdempotency reread = holdIdempotencyRepository
                        .findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey)
                        .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT));

                if (!reread.getSeatIdsKey().equals(seatIdsKey)) {
                    throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                if (reread.isCompleted()) {
                    return reread;
                }
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }

        }

        return createHoldIdempotency(userId, eventId, idempotencyKey, seatIdsKey, expiresAt, now);
    }

    private boolean isConstraint(Throwable e, String constraintName) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return constraintName.equalsIgnoreCase(cve.getConstraintName());
            }
            cur = cur.getCause();
        }
        return false;
    }


    private List<Long> getNormalizedSeatIds(List<Long> seatIds) {
        List<Long> normalized = SeatIdsCodec.normalize(seatIds);
        if (normalized.isEmpty()) {
            throw new BusinessRuleViolationException(ErrorCode.INVALID_SEAT_SET);
        }
        return normalized;
    }
}
