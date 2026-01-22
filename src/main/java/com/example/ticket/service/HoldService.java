package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.HoldDto.*;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldGroupSeat;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.HoldIdempotency;
import com.example.ticket.domain.idempotency.SeatIdsCodec;
import com.example.ticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final Clock clock;
    private final SeatRepository seatRepository;
    private final HoldIdempotencyRepository holdIdempotencyRepository;
    private final HoldGroupRepository holdGroupRepository;
    private final HoldGroupSeatRepository holdGroupSeatRepository;

    @Transactional
    public HoldCreateResponse hold(long userId, long eventId, HoldCreateRequest request, String idempotencyKey) {
        Optional<HoldIdempotency> idempotency = holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey);
        String seatIdsKey = SeatIdsCodec.toCanonicalString(request.seatIds());
        if (idempotency.isPresent()) {

            if (!idempotency.get().getEventId().equals(eventId) || !idempotency.get().getSeatIdsHash().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return HoldCreateResponse.from(
                    idempotency.get().getEventId(),
                    idempotency.get().getHoldGroupId(),
                    idempotency.get().getSeatCount()
            );
        }

        Instant expiresAt = HoldTimes.holdUntil(clock);
        int updated = seatRepository.holdIfAllAvailable(eventId, request.seatIds(), userId, expiresAt);
        if (updated != request.seatIds().size()) {
            throw new BusinessRuleViolationException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        HoldGroup holdGroup = createHoldGroup(userId, eventId, expiresAt);
        int seatCount = createHoldGroupSeats(request.seatIds(), holdGroup.getId());

        HoldIdempotency savedHoldIdempotency = holdIdempotencyRepository.save(HoldIdempotency.create(
                userId,
                idempotencyKey,
                eventId,
                seatIdsKey,
                holdGroup.getId(),
                expiresAt, seatCount)
        );

        return HoldCreateResponse.from(savedHoldIdempotency);
    }

    @Transactional
    protected int createHoldGroupSeats(List<Long> seatIds, UUID holdGroupId) {
        List<HoldGroupSeat> savedHoldGroupSeats = holdGroupSeatRepository.saveAll(
                seatIds.stream()
                        .map(seatId -> HoldGroupSeat.create(holdGroupId, seatId))
                        .toList());
        return savedHoldGroupSeats.size();
    }

    @Retryable(value = DuplicateKeyException.class, maxRetries = 1)
    @Transactional
    protected HoldGroup createHoldGroup(long userId, long eventId, Instant holdUntil) {
        return holdGroupRepository.save(HoldGroup.create(eventId, userId, holdUntil));
    }

}
