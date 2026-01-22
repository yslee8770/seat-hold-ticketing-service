package com.example.ticket.service;

import com.example.ticket.api.admin.dto.AdminSeatDto.AdminSeatBulkUpsertRequest;
import com.example.ticket.api.admin.dto.AdminSeatDto.AdminSeatBulkUpsertResponse;
import com.example.ticket.api.admin.dto.AdminSeatDto.AdminSeatCreateDto;
import com.example.ticket.api.admin.dto.AdminSeatSummaryDto.AdminSeatSummaryResponse;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.EventStatus;
import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.EventRepository;
import com.example.ticket.repository.SeatRepository;
import com.example.ticket.repository.dto.SeatStatusCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AdminSeatServiceTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    SeatRepository seatRepository;

    @InjectMocks
    AdminSeatService adminSeatService;

    @Test
    void bulkReplace_success_when_event_is_draft() {
        // given
        long eventId = 1L;
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.DRAFT);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        AdminSeatBulkUpsertRequest req = new AdminSeatBulkUpsertRequest(List.of(
                new AdminSeatCreateDto("A", "1", 1000L),
                new AdminSeatCreateDto("A", "2", 2000L),
                new AdminSeatCreateDto("B", "1", 3000L)
        ));

        // when
        AdminSeatBulkUpsertResponse res = adminSeatService.bulkReplace(eventId, req);

        // then
        assertThat(res).isNotNull();
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.createdCount()).isEqualTo(3);

        InOrder inOrder = inOrder(seatRepository);
        inOrder.verify(seatRepository).deleteAllByEventId(eventId);

        ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
        inOrder.verify(seatRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);

        inOrder.verifyNoMoreInteractions();

        verify(eventRepository).findById(eventId);
        verifyNoMoreInteractions(eventRepository);
    }

    @Test
    void bulkReplace_throws_EVENT_NOT_FOUND_when_event_missing() {
        // given
        long eventId = 1L;
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        AdminSeatBulkUpsertRequest req = new AdminSeatBulkUpsertRequest(List.of(
                new AdminSeatCreateDto("A", "1", 1000L)
        ));

        // when / then
        assertThatThrownBy(() -> adminSeatService.bulkReplace(eventId, req))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException e = (BusinessRuleViolationException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND);
                });

        verify(eventRepository).findById(eventId);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void bulkReplace_throws_EVENT_NOT_DRAFT_when_event_not_draft() {
        // given
        long eventId = 1L;
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.OPEN);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        AdminSeatBulkUpsertRequest req = new AdminSeatBulkUpsertRequest(List.of(
                new AdminSeatCreateDto("A", "1", 1000L)
        ));

        // when / then
        assertThatThrownBy(() -> adminSeatService.bulkReplace(eventId, req))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException e = (BusinessRuleViolationException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_DRAFT);
                });

        verify(eventRepository).findById(eventId);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void bulkReplace_throws_DUPLICATED_SEAT_IN_REQUEST_when_request_has_duplicates() {
        // given
        long eventId = 1L;
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.DRAFT);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        AdminSeatBulkUpsertRequest req = new AdminSeatBulkUpsertRequest(List.of(
                new AdminSeatCreateDto("A", "1", 1000L),
                new AdminSeatCreateDto("A", "1", 2000L)
        ));

        // when / then
        assertThatThrownBy(() -> adminSeatService.bulkReplace(eventId, req))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException e = (BusinessRuleViolationException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATED_SEAT_IN_REQUEST);
                });

        verify(eventRepository).findById(eventId);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void getSummary_throws_EVENT_NOT_FOUND_when_event_missing() {
        // given
        long eventId = 1L;
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> adminSeatService.getSummary(eventId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(ex -> {
                    BusinessRuleViolationException e = (BusinessRuleViolationException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND);
                });

        verify(eventRepository).findById(eventId);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void getSummary_counts_each_status_and_fills_missing_as_zero() {
        // given
        long eventId = 1L;
        Event event = mock(Event.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        when(seatRepository.countByStatus(eventId)).thenReturn(List.of(
                new SeatStatusCount(SeatStatus.AVAILABLE, 10L),
                new SeatStatusCount(SeatStatus.SOLD, 3L)
        ));

        // when
        AdminSeatSummaryResponse res = adminSeatService.getSummary(eventId);

        // then
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.availableCount()).isEqualTo(10L);
        assertThat(res.heldCount()).isEqualTo(0L);
        assertThat(res.soldCount()).isEqualTo(3L);

        verify(eventRepository).findById(eventId);
        verify(seatRepository).countByStatus(eventId);
        verifyNoMoreInteractions(eventRepository, seatRepository);
    }
}