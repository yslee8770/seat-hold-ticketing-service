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
import com.example.ticket.repository.*;
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
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    private static final String UK_HOLD_GROUP_SEATS = "uk_hold_group_seats_seat_id_event_id";
    private static final String UK_HOLD_IDEMPOTENCY = "uk_hold_idempotency_user_event_key";

    @Mock HoldIdempotencyRepository holdIdempotencyRepository;
    @Mock HoldGroupRepository holdGroupRepository;
    @Mock HoldGroupSeatRepository holdGroupSeatRepository;
    @Mock SeatRepository seatRepository;
    @Mock EventRepository eventRepository;

    private HoldService holdService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC);
        holdService = new HoldService(
                clock,
                holdIdempotencyRepository,
                holdGroupRepository,
                holdGroupSeatRepository,
                seatRepository,
                eventRepository
        );
    }

    private void stubEventOnSale(long eventId, Instant now) {
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.OPEN);
        when(event.getSalesOpenAt()).thenReturn(now.minusSeconds(1));
        when(event.getSalesCloseAt()).thenReturn(now.plusSeconds(60));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    }

    @Test
    void hold_whenIdempotencyCompleted_exists_returnsSameResponse_andNoSideEffects() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of(3L, 1L, 3L));
        String seatIdsKey = "1,3";

        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, seatIdsKey,
                999L, HoldTimes.holdUntil(clock), 2
        );

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        HoldCreateResponse res = holdService.hold(userId, eventId, req, key);

        assertEquals(eventId, res.eventId());
        assertEquals(2, res.seatCount());

        verify(holdGroupRepository, never()).save(any());
        verify(holdGroupSeatRepository, never()).saveAll(any());
        verify(seatRepository, never()).countByEventIdAndIdInAndStatus(anyLong(), anyList(), any());
        verify(holdIdempotencyRepository, never()).save(any());
        verify(holdIdempotencyRepository, never()).deleteStaleInProgress(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void hold_whenIdempotencyExists_butSeatIdsMismatch_throwsIdempotencyConflict() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of(2L));
        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, "1,3",
                999L, HoldTimes.holdUntil(clock), 2
        );

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, key)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());

        verify(holdGroupRepository, never()).save(any());
        verify(holdGroupSeatRepository, never()).saveAll(any());
        verify(seatRepository, never()).countByEventIdAndIdInAndStatus(anyLong(), anyList(), any());
        verify(holdIdempotencyRepository, never()).save(any());
    }

    @Test
    void hold_whenSeatIdsEmpty_throwsInvalidSeatSet() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of());

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, "k1")
        );
        assertEquals(ErrorCode.INVALID_SEAT_SET, ex.getErrorCode());

        verify(holdIdempotencyRepository, never()).findByUserIdAndEventIdAndIdempotencyKey(anyLong(), anyLong(), anyString());
        verify(holdIdempotencyRepository, never()).save(any());
        verify(holdGroupRepository, never()).save(any());
        verify(holdGroupSeatRepository, never()).saveAll(any());
        verify(seatRepository, never()).countByEventIdAndIdInAndStatus(anyLong(), anyList(), any());
    }

    @Test
    void hold_whenSeatNotAvailable_byCountCheck_throwsSeatNotAvailable_andDoesNotCompleteIdempotency() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of(1L, 2L));
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.empty());

        when(holdGroupRepository.findActiveIds(eq(userId), eq(eventId), any()))
                .thenReturn(List.of());

        HoldIdempotency savedIdem =
                HoldIdempotency.create(userId, key, eventId, "1,2", HoldTimes.holdUntil(clock));
        when(holdIdempotencyRepository.save(any(HoldIdempotency.class))).thenReturn(savedIdem);

        HoldGroup hg = mock(HoldGroup.class);
        when(holdGroupRepository.save(any(HoldGroup.class))).thenReturn(hg);

        when(seatRepository.countByEventIdAndIdInAndStatus(eq(eventId), anyList(), eq(SeatStatus.AVAILABLE)))
                .thenReturn(1L);

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, key)
        );
        assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, ex.getErrorCode());

        verify(holdIdempotencyRepository, times(1)).save(any(HoldIdempotency.class));

        verify(holdGroupSeatRepository, never()).saveAll(any());

        verify(holdGroupRepository, times(1)).save(any(HoldGroup.class));
        verify(seatRepository, times(1))
                .countByEventIdAndIdInAndStatus(eq(eventId), anyList(), eq(SeatStatus.AVAILABLE));
    }


    @Test
    void hold_whenSeatAlreadyHeld_byUniqueConstraint_throwsSeatNotAvailable_andDoesNotCompleteIdempotency() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of(1L, 2L));
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.empty());

        HoldIdempotency savedIdem = HoldIdempotency.create(userId, key, eventId, "1,2", HoldTimes.holdUntil(clock));
        when(holdIdempotencyRepository.save(any(HoldIdempotency.class))).thenReturn(savedIdem);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);
        when(holdGroupRepository.save(any(HoldGroup.class))).thenReturn(hg);

        when(seatRepository.countByEventIdAndIdInAndStatus(eq(eventId), anyList(), eq(SeatStatus.AVAILABLE)))
                .thenReturn(2L);

        when(holdGroupSeatRepository.saveAll(anyList()))
                .thenThrow(dataIntegrity(UK_HOLD_GROUP_SEATS));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.hold(userId, eventId, req, key)
        );
        assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, ex.getErrorCode());

        verify(holdIdempotencyRepository, times(1)).save(any(HoldIdempotency.class));
        verify(holdIdempotencyRepository, times(1)).save(any(HoldIdempotency.class));
    }

    @Test
    void hold_success_createsGroupSeats_andCompletesIdempotency() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        HoldCreateRequest req = new HoldCreateRequest(List.of(3L, 1L, 3L));
        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.empty());

        HoldIdempotency firstSaved = HoldIdempotency.create(userId, key, eventId, "1,3", HoldTimes.holdUntil(clock));
        when(holdIdempotencyRepository.save(any(HoldIdempotency.class))).thenReturn(firstSaved);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(100L);
        when(holdGroupRepository.save(any(HoldGroup.class))).thenReturn(hg);

        Instant expectedExpiresAt = HoldTimes.holdUntil(clock);

        when(seatRepository.countByEventIdAndIdInAndStatus(eq(eventId), anyList(), eq(SeatStatus.AVAILABLE)))
                .thenReturn(2L);

        when(holdGroupSeatRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(holdIdempotencyRepository.save(firstSaved)).thenReturn(firstSaved);

        HoldCreateResponse res = holdService.hold(userId, eventId, req, key);

        assertEquals(eventId, res.eventId());
        assertEquals(2, res.seatCount());

        verify(holdGroupSeatRepository, times(1)).saveAll(argThat(iterable -> {
            List<HoldGroupSeat> list = StreamSupport.stream(iterable.spliterator(), false).toList();

            assertEquals(2, list.size());
            assertEquals(1L, list.get(0).getSeatId());
            assertEquals(3L, list.get(1).getSeatId());

            assertEquals(eventId, list.get(0).getEventId());
            assertEquals(100L, list.get(0).getHoldGroupId());
            assertEquals(expectedExpiresAt, list.get(0).getExpiresAt());

            return true;
        }));

        verify(holdIdempotencyRepository, times(2)).save(any(HoldIdempotency.class));

        assertNotNull(firstSaved.getHoldGroupId());
        assertNotNull(firstSaved.getSeatCount());
        assertEquals(100L, firstSaved.getHoldGroupId());
        assertEquals(2, firstSaved.getSeatCount());
        assertEquals(expectedExpiresAt, firstSaved.getExpiresAt());
    }

    @Test
    void createHoldIdempotency_whenDuplicateKey_returnsExisting_ifCompleted_andSameSeatIdsKey() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";
        String seatIdsKey = "1,3";

        Instant now = HoldTimes.now(clock);
        Instant expiresAt = HoldTimes.holdUntil(clock);

        HoldIdempotency existing = HoldIdempotency.create(
                userId, key, eventId, seatIdsKey,
                100L, expiresAt, 2
        );

        when(holdIdempotencyRepository.save(any(HoldIdempotency.class)))
                .thenThrow(dataIntegrity(UK_HOLD_IDEMPOTENCY));

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(existing));

        HoldIdempotency result = holdService.createHoldIdempotency(
                userId, eventId, key, seatIdsKey, expiresAt, now
        );

        assertSame(existing, result);
    }

    @Test
    void createHoldIdempotency_whenDuplicateKey_butSeatIdsKeyDifferent_throwsIdempotencyConflict() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";

        Instant now = HoldTimes.now(clock);
        Instant expiresAt = HoldTimes.holdUntil(clock);

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
                        userId, eventId, key, "2", expiresAt, now
                )
        );
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    void resolveOrCreateIdempotency_whenExistingInProgress_andNotExpired_throwsInProgress() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";
        String seatIdsKey = "1,3";

        Instant now = HoldTimes.now(clock);
        Instant expiresAt = HoldTimes.holdUntil(clock);

        HoldIdempotency inProgress = HoldIdempotency.create(userId, key, eventId, seatIdsKey, expiresAt);

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(inProgress));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> holdService.resolveOrCreateIdempotency(userId, eventId, key, seatIdsKey, now, expiresAt)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_IN_PROGRESS, ex.getErrorCode());
    }

    @Test
    void resolveOrCreateIdempotency_whenExistingInProgress_butExpired_deletesAndCreatesNew() {
        long userId = 1L;
        long eventId = 10L;
        String key = "k1";
        String seatIdsKey = "1,3";

        Instant now = HoldTimes.now(clock);
        Instant expiredNow = now.minusSeconds(1);
        Instant newExpiresAt = HoldTimes.holdUntil(clock);

        HoldIdempotency stale = HoldIdempotency.create(userId, key, eventId, seatIdsKey, expiredNow);

        when(holdIdempotencyRepository.findByUserIdAndEventIdAndIdempotencyKey(userId, eventId, key))
                .thenReturn(Optional.of(stale));

        when(holdIdempotencyRepository.deleteStaleInProgress(userId, eventId, key, now))
                .thenReturn(1);

        HoldIdempotency newlySaved = HoldIdempotency.create(userId, key, eventId, seatIdsKey, newExpiresAt);
        when(holdIdempotencyRepository.save(any(HoldIdempotency.class))).thenReturn(newlySaved);

        HoldIdempotency result = holdService.resolveOrCreateIdempotency(userId, eventId, key, seatIdsKey, now, newExpiresAt);
        assertSame(newlySaved, result);
    }

    private static DataIntegrityViolationException dataIntegrity(String constraintName) {
        ConstraintViolationException cve =
                new ConstraintViolationException("constraint violated", new SQLException("dup"), constraintName);
        return new DataIntegrityViolationException("DIE", cve);
    }
}
