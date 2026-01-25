package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateRequest;
import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldGroupSeat;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.HoldIdempotency;
import com.example.ticket.domain.idempotency.SeatIdsCodec;
import com.example.ticket.repository.HoldGroupRepository;
import com.example.ticket.repository.HoldGroupSeatRepository;
import com.example.ticket.repository.HoldIdempotencyRepository;
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

    @Transactional
    public HoldCreateResponse hold(long userId, long eventId, HoldCreateRequest request, String idempotencyKey) {
        String seatIdsKey = SeatIdsCodec.toCanonicalString(request.seatIds());

        Optional<HoldIdempotency> idempotency = holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey);
        if (idempotency.isPresent()) {
            if (!idempotency.get().getSeatIdsKey().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return HoldCreateResponse.from(idempotency.get());
        }

        Instant expiresAt = HoldTimes.holdUntil(clock);
        HoldGroup holdGroup = holdGroupRepository.save(HoldGroup.create(userId, expiresAt));
        List<Long> seatIds = getNormalizedSeatIds(request.seatIds());

        int savedHoldGroupSeatsCount = createHoldSeats(eventId, seatIds, expiresAt, holdGroup);
        return HoldCreateResponse.from(
                createHoldIdempotency(
                        userId,
                        eventId,
                        idempotencyKey,
                        seatIdsKey,
                        holdGroup,
                        expiresAt,
                        savedHoldGroupSeatsCount)
        );
    }

    @Transactional
    public HoldIdempotency createHoldIdempotency(long userId, long eventId, String idempotencyKey, String seatIdsKey, HoldGroup holdGroup, Instant expiresAt, int savedHoldGroupSeatsCount) {
        try {
            return holdIdempotencyRepository.save(
                    HoldIdempotency.create(
                            userId,
                            idempotencyKey,
                            eventId,
                            seatIdsKey,
                            holdGroup.getId(),
                            expiresAt,
                            savedHoldGroupSeatsCount)
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

            return existing;
        }
    }

    @Transactional
    public int createHoldSeats(long eventId, List<Long> seatIds, Instant expiresAt, HoldGroup holdGroup) {
        try {
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
