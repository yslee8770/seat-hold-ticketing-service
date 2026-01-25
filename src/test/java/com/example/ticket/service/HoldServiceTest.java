package com.example.ticket.service;


import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateRequest;
import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldGroupSeat;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.HoldIdempotency;
import com.example.ticket.repository.HoldGroupRepository;
import com.example.ticket.repository.HoldGroupSeatRepository;
import com.example.ticket.repository.HoldIdempotencyRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    private static final String UK_HOLD_GROUP_SEATS = "uk_hold_group_seats_seat_id_event_id";
    private static final String UK_HOLD_IDEMPOTENCY = "uk_hold_idempotency_user_event_key"; // <- 네 코드/DDL 기준으로 수정

    @Mock HoldIdempotencyRepository holdIdempotencyRepository;
    @Mock HoldGroupRepository holdGroupRepository;
    @Mock HoldGroupSeatRepository holdGroupSeatRepository;

    private HoldService holdService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC);
        holdService = new HoldService(clock, holdIdempotencyRepository, holdGroupRepository, holdGroupSeatRepository);
    }

    @Test
    void hold_whenIdempotencyExists_returnsSameResponse_withoutSideEffects() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        HoldCreateRequest req = new HoldCreateRequest(List.of(3L, 1L, 3L)); // normalize -> [1,3]
        String seatIdsKey = "1,3";

        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, seatIdsKey,
                999L, Instant.parse("2026-01-25T00:01:30Z"), 2
        );

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        HoldCreateResponse res = holdService.hold(userId, eventId, req, key);

        assertEquals(eventId, res.eventId());
        assertEquals(2, res.seatCount());

        verify(holdGroupRepository, never()).save(any());
        verify(holdGroupSeatRepository, never()).saveAll(any());
        verify(holdIdempotencyRepository, never()).save(any());
    }

    @Test
    void hold_whenIdempotencyExistsButSeatIdsMismatch_throwsIdempotencyConflict() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        HoldCreateRequest req = new HoldCreateRequest(List.of(2L)); // seatIdsKey "2"
        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, "1,3",
                999L, Instant.parse("2026-01-25T00:01:30Z"), 2
        );

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, key)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    void hold_whenSeatIdsEmpty_throwsInvalidSeatSet() {
        long userId = 1L;
        long eventId = 10L;

        HoldCreateRequest req = new HoldCreateRequest(List.of());
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // HoldGroup 저장 호출 여부는 구현 순서에 따라 달라질 수 있어 검증하지 않음(리팩토링에 덜 취약하게).
        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, "k1")
        );
        assertEquals(ErrorCode.INVALID_SEAT_SET, ex.getErrorCode());
    }

    @Test
    void hold_whenSeatAlreadyHeld_throwsSeatNotAvailable_andDoesNotSaveIdempotency() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        HoldCreateRequest req = new HoldCreateRequest(List.of(1L, 2L));
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.empty());

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);
        when(holdGroupRepository.save(any(HoldGroup.class))).thenReturn(hg);

        when(holdGroupSeatRepository.saveAll(anyList()))
                .thenThrow(dataIntegrity(UK_HOLD_GROUP_SEATS));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, key)
        );
        assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, ex.getErrorCode());

        verify(holdIdempotencyRepository, never()).save(any());
    }

    @Test
    void hold_success_createsGroupSeatsAndIdempotency() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        HoldCreateRequest req = new HoldCreateRequest(List.of(3L, 1L, 3L)); // normalize -> [1,3]
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.empty());

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);
        when(holdGroupRepository.save(any(HoldGroup.class))).thenReturn(hg);

        Instant expectedExpiresAt = HoldTimes.holdUntil(clock);

        when(holdGroupSeatRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        HoldIdempotency saved = HoldIdempotency.create(
                userId, key, eventId, "1,3",
                100L, expectedExpiresAt, 2
        );
        when(holdIdempotencyRepository.save(any(HoldIdempotency.class))).thenReturn(saved);

        HoldCreateResponse res = holdService.hold(userId, eventId, req, key);

        assertEquals(eventId, res.eventId());
        assertEquals(2, res.seatCount());

        ArgumentCaptor<List<HoldGroupSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(holdGroupSeatRepository).saveAll(captor.capture());

        List<HoldGroupSeat> seats = captor.getValue();
        assertEquals(2, seats.size());
        assertEquals(1L, seats.get(0).getSeatId());
        assertEquals(3L, seats.get(1).getSeatId());
        assertEquals(eventId, seats.get(0).getEventId());
        assertEquals(100L, seats.get(0).getHoldGroupId());
        assertEquals(expectedExpiresAt, seats.get(0).getExpiresAt());
    }

    @Test
    void createHoldIdempotency_whenDuplicateKey_returnsExistingIfSameSeatIdsKey() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";
        String seatIdsKey = "1,3";
        Instant expiresAt = HoldTimes.holdUntil(clock);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);

        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, seatIdsKey,
                100L, expiresAt, 2
        );

        when(holdIdempotencyRepository.save(any(HoldIdempotency.class)))
                .thenThrow(dataIntegrity(UK_HOLD_IDEMPOTENCY));

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        HoldIdempotency result = holdService.createHoldIdempotency(
                userId, eventId, key, seatIdsKey, hg, expiresAt, 2
        );

        assertSame(existing, result);
    }

    @Test
    void createHoldIdempotency_whenDuplicateKey_butSeatIdsKeyDifferent_throwsIdempotencyConflict() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";
        Instant expiresAt = HoldTimes.holdUntil(clock);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);

        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, "1,3",
                100L, expiresAt, 2
        );

        when(holdIdempotencyRepository.save(any(HoldIdempotency.class)))
                .thenThrow(dataIntegrity(UK_HOLD_IDEMPOTENCY));

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.createHoldIdempotency(
                        userId, eventId, key, "2", hg, expiresAt, 1
                )
        );
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    private static DataIntegrityViolationException dataIntegrity(String constraintName) {
        ConstraintViolationException cve =
                new ConstraintViolationException("constraint violated", new SQLException("dup"), constraintName);
        return new DataIntegrityViolationException("DIE", cve);
    }
}