package com.example.ticket.service;

import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldGroupSeat;
import com.example.ticket.repository.HoldGroupRepository;
import com.example.ticket.repository.HoldGroupSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class HoldInfoService {

    private final HoldGroupRepository holdGroupRepository;
    private final HoldGroupSeatRepository holdGroupSeatRepository;

    @Transactional
    public int createHoldGroupSeats(List<Long> seatIds, UUID holdGroupId) {
        List<HoldGroupSeat> savedHoldGroupSeats = holdGroupSeatRepository.saveAll(
                seatIds.stream()
                        .map(seatId -> HoldGroupSeat.create(holdGroupId, seatId))
                        .toList());
        return savedHoldGroupSeats.size();
    }

    @Retryable(value = DuplicateKeyException.class, maxRetries = 1)
    @Transactional
    public HoldGroup createHoldGroup(long userId, long eventId, Instant holdUntil) {
        return holdGroupRepository.save(HoldGroup.create(eventId, userId, holdUntil));
    }
}
