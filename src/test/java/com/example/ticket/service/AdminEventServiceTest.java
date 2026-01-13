package com.example.ticket.service;

import com.example.ticket.api.admin.dto.AdminEventDto.*;
import com.example.ticket.common.BusinessRuleViolationException;
import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.EventStatus;
import com.example.ticket.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @Mock
    EventRepository eventRepository;

    @InjectMocks
    AdminEventService service;

    @Test
    void createDraft_rejects_invalid_sales_window() {
        // given
        Instant openAt = Instant.parse("2026-01-13T10:00:00Z");
        Instant closeAt = Instant.parse("2026-01-13T10:00:00Z"); // open == close (invalid)
        AdminEventCreateRequest req = new AdminEventCreateRequest("concert", openAt, closeAt);

        // when / then
        assertThatThrownBy(() -> service.createDraft(req))
                .isInstanceOf(BusinessRuleViolationException.class);

        verifyNoInteractions(eventRepository);
    }

    @Test
    void createDraft_saves_event_and_returns_response() {
        // given
        Instant openAt = Instant.parse("2026-01-13T10:00:00Z");
        Instant closeAt = Instant.parse("2026-01-13T12:00:00Z");
        AdminEventCreateRequest req = new AdminEventCreateRequest("concert", openAt, closeAt);

        Event saved = mock(Event.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getTitle()).thenReturn("concert");
        when(saved.getStatus()).thenReturn(EventStatus.DRAFT);
        when(saved.getSalesOpenAt()).thenReturn(openAt);
        when(saved.getSalesCloseAt()).thenReturn(closeAt);

        when(eventRepository.save(any(Event.class))).thenReturn(saved);

        // when
        AdminEventResponse res = service.createDraft(req);

        // then
        assertThat(res.eventId()).isEqualTo(1L);
        assertThat(res.title()).isEqualTo("concert");
        assertThat(res.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(res.salesOpenAt()).isEqualTo(openAt);
        assertThat(res.salesCloseAt()).isEqualTo(closeAt);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void open_calls_event_open_and_returns_response() {
        // given
        long eventId = 10L;

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getTitle()).thenReturn("concert");
        when(event.getStatus()).thenReturn(EventStatus.OPEN);
        when(event.getSalesOpenAt()).thenReturn(Instant.parse("2026-01-13T10:00:00Z"));
        when(event.getSalesCloseAt()).thenReturn(Instant.parse("2026-01-13T12:00:00Z"));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // when
        AdminEventResponse res = service.open(eventId);

        // then
        verify(eventRepository).findById(eventId);
        verify(event).open();
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.status()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    void close_calls_event_close_and_returns_response() {
        // given
        long eventId = 10L;

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getTitle()).thenReturn("concert");
        when(event.getStatus()).thenReturn(EventStatus.CLOSED);
        when(event.getSalesOpenAt()).thenReturn(Instant.parse("2026-01-13T10:00:00Z"));
        when(event.getSalesCloseAt()).thenReturn(Instant.parse("2026-01-13T12:00:00Z"));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // when
        AdminEventResponse res = service.close(eventId);

        // then
        verify(eventRepository).findById(eventId);
        verify(event).close();
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.status()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    void open_throws_when_event_not_found() {
        // given
        long eventId = 999L;
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.open(eventId))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(eventRepository).findById(eventId);
    }
}
