package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.HoldDto.*;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.HoldIdempotency;
import com.example.ticket.domain.idempotency.SeatIdsCodec;
import com.example.ticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final Clock clock;
    private final SeatRepository seatRepository;
    private final HoldIdempotencyRepository holdIdempotencyRepository;
    private final HoldInfoService holdInfoService;

    @Transactional
    public HoldCreateResponse hold(long userId, long eventId, HoldCreateRequest request, String idempotencyKey) {
        Optional<HoldIdempotency> idempotency = holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey);
        String seatIdsKey = SeatIdsCodec.toCanonicalString(request.seatIds());
        if (idempotency.isPresent()) {

            if (!idempotency.get().getEventId().equals(eventId) || !idempotency.get().getSeatIdsKey().equals(seatIdsKey)) {
                throw new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return HoldCreateResponse.from(idempotency.get());
        }

        Instant expiresAt = HoldTimes.holdUntil(clock);
        int updated = seatRepository.holdIfAllAvailable(eventId, SeatIdsCodec.normalize(request.seatIds()), userId, expiresAt);
        if (updated != request.seatIds().size()) {
            throw new BusinessRuleViolationException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        HoldGroup holdGroup = holdInfoService.createHoldGroup(userId, eventId, expiresAt);
        int seatCount = holdInfoService.createHoldGroupSeats(SeatIdsCodec.normalize(request.seatIds()), holdGroup.getId());

        try {
            HoldIdempotency savedHoldIdempotency = holdIdempotencyRepository.save(
                    HoldIdempotency.create(
                            userId,
                            idempotencyKey,
                            eventId,
                            seatIdsKey,
                            holdGroup.getId(),
                            expiresAt, seatCount)
            );
            return HoldCreateResponse.from(savedHoldIdempotency);
        } catch (DuplicateKeyException e) {
            HoldIdempotency existing = holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, idempotencyKey)
                    .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.IDEMPOTENCY_CONFLICT));
            return HoldCreateResponse.from(existing);
        }
    }
}
