package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.AdminExpireSweepDto.AdminExpireSweepResponse;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.repository.HoldGroupRepository;
import com.example.ticket.repository.HoldGroupSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExpireSweepService {

    private final Clock clock;
    private final HoldGroupSeatRepository holdGroupSeatRepository;
    private final HoldGroupRepository holdGroupRepository;

    @Transactional
    public AdminExpireSweepResponse sweepExpired() {
        Instant now = HoldTimes.now(clock);
        int sweepCount = holdGroupSeatRepository.deleteAllByExpiresAtLessThanEqual(now);
        holdGroupRepository.deleteAllByExpiresAtLessThanEqual(now);

        return AdminExpireSweepResponse.from(sweepCount, now);
    }
}
