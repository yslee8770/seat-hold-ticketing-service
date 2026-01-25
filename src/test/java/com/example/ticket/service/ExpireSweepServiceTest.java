package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.AdminExpireSweepDto.AdminExpireSweepResponse;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.repository.HoldGroupRepository;
import com.example.ticket.repository.HoldGroupSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpireSweepServiceTest {

    @Mock HoldGroupSeatRepository holdGroupSeatRepository;
    @Mock HoldGroupRepository holdGroupRepository;

    private ExpireSweepService expireSweepService;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC);
        expireSweepService = new ExpireSweepService(fixed, holdGroupSeatRepository, holdGroupRepository);
    }

    @Test
    void sweepExpired_deletesExpiredSeatsAndGroups_andReturnsCountAndNow() {
        // given
        Instant now = HoldTimes.now(Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC));
        when(holdGroupSeatRepository.deleteAllByExpiresAtLessThanEqual(now)).thenReturn(7);

        // when
        AdminExpireSweepResponse res = expireSweepService.sweepExpired();

        // then: repo calls
        verify(holdGroupSeatRepository, times(1)).deleteAllByExpiresAtLessThanEqual(now);
        verify(holdGroupRepository, times(1)).deleteAllByExpiresAtLessThanEqual(now);
        verifyNoMoreInteractions(holdGroupSeatRepository, holdGroupRepository);

        // then: response mapping
        AdminExpireSweepResponse expected = AdminExpireSweepResponse.from(7, now);
        assertEquals(expected, res);
    }

    @Test
    void sweepExpired_whenNothingExpired_returnsZero() {
        Instant now = HoldTimes.now(Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC));
        when(holdGroupSeatRepository.deleteAllByExpiresAtLessThanEqual(now)).thenReturn(0);

        AdminExpireSweepResponse res = expireSweepService.sweepExpired();

        verify(holdGroupSeatRepository).deleteAllByExpiresAtLessThanEqual(now);
        verify(holdGroupRepository).deleteAllByExpiresAtLessThanEqual(now);

        AdminExpireSweepResponse expected = AdminExpireSweepResponse.from(0, now);
        assertEquals(expected, res);
    }
}