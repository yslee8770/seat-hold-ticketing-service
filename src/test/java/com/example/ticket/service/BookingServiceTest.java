package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.BookingDto.BookingDetailResponse;
import com.example.ticket.api.ticket.dto.BookingDto.BookingListResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.booking.Booking;
import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.domain.booking.BookingStatus;
import com.example.ticket.repository.BookingItemRepository;
import com.example.ticket.repository.BookingRepository;
import com.example.ticket.repository.dto.BookingItemAgg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingItemRepository bookingItemRepository;

    @InjectMocks BookingService bookingService;

    @Test
    void list_eventId_null_and_no_bookings_returns_empty_and_does_not_call_aggregate() {
        long userId = 1L;

        when(bookingRepository.findByUserId(userId)).thenReturn(List.of());

        BookingListResponse res = bookingService.list(userId, null);

        assertThat(res.bookings()).isEmpty();
        verify(bookingRepository).findByUserId(userId);
        verify(bookingRepository, never()).findByUserIdAndEventId(anyLong(), anyLong());
        verifyNoInteractions(bookingItemRepository);
    }

    @Test
    void list_eventId_null_aggregates_by_bookingIds_and_maps_each_booking() {
        long userId = 1L;

        Booking b1 = mockBookingForList(101L, 10L, "ptx-1", BookingStatus.CONFIRMED, Instant.parse("2026-02-01T00:00:00Z"));
        Booking b2 = mockBookingForList(102L, 11L, "ptx-2", BookingStatus.CONFIRMED, Instant.parse("2026-02-02T00:00:00Z"));
        when(bookingRepository.findByUserId(userId)).thenReturn(List.of(b1, b2));

        when(bookingItemRepository.aggregateByBookingIds(List.of(101L, 102L)))
                .thenReturn(List.of(new BookingItemAgg(101L, 2L, 300L)));

        BookingListResponse res = bookingService.list(userId, null);

        assertThat(res.bookings()).hasSize(2);

        var s1 = res.bookings().get(0);
        assertThat(s1.bookingId()).isEqualTo(101L);
        assertThat(s1.itemCount()).isEqualTo(2L);
        assertThat(s1.totalAmount()).isEqualTo(300L);

        var s2 = res.bookings().get(1);
        assertThat(s2.bookingId()).isEqualTo(102L);
        assertThat(s2.itemCount()).isEqualTo(0L);
        assertThat(s2.totalAmount()).isEqualTo(0L);

        verify(bookingRepository).findByUserId(userId);
        verify(bookingItemRepository).aggregateByBookingIds(List.of(101L, 102L));
    }

    @Test
    void list_eventId_present_uses_findByUserIdAndEventId() {
        long userId = 1L;
        long eventId = 10L;

        Booking b1 = mockBookingForList(101L, eventId, "ptx-1", BookingStatus.CONFIRMED, Instant.now());
        when(bookingRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of(b1));
        when(bookingItemRepository.aggregateByBookingIds(List.of(101L)))
                .thenReturn(List.of(new BookingItemAgg(101L, 1L, 100L)));

        BookingListResponse res = bookingService.list(userId, eventId);

        assertThat(res.bookings()).hasSize(1);
        assertThat(res.bookings().get(0).eventId()).isEqualTo(eventId);

        verify(bookingRepository).findByUserIdAndEventId(userId, eventId);
        verify(bookingRepository, never()).findByUserId(anyLong());
        verify(bookingItemRepository).aggregateByBookingIds(List.of(101L));
    }

    @Test
    void detail_found_returns_items_and_total_sum() {
        long userId = 1L;
        long bookingId = 777L;

        Booking booking = mockBookingForDetail(bookingId, 10L, userId, "ptx-1", BookingStatus.CONFIRMED, Instant.parse("2026-02-01T00:00:00Z"));
        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(booking));

        List<BookingItem> items = List.of(
                BookingItem.create(bookingId, 1L, 500L),
                BookingItem.create(bookingId, 2L, 700L)
        );
        when(bookingItemRepository.findAllByBookingId(bookingId)).thenReturn(items);

        BookingDetailResponse res = bookingService.detail(userId, bookingId);

        assertThat(res.bookingId()).isEqualTo(bookingId);
        assertThat(res.userId()).isEqualTo(userId);
        assertThat(res.totalAmount()).isEqualTo(1200L);
        assertThat(res.items()).hasSize(2);

        verify(bookingRepository).findByIdAndUserId(bookingId, userId);
        verify(bookingItemRepository).findAllByBookingId(bookingId);
    }

    @Test
    void detail_not_found_throws_BOOKING_NOT_FOUND_and_does_not_load_items() {
        long userId = 1L;
        long bookingId = 777L;

        when(bookingRepository.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.empty());

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> bookingService.detail(userId, bookingId),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_NOT_FOUND);
        verify(bookingRepository).findByIdAndUserId(bookingId, userId);
        verifyNoInteractions(bookingItemRepository);
    }

    private static Booking mockBookingForList(
            long bookingId,
            long eventId,
            String paymentTxId,
            BookingStatus status,
            Instant createdAt
    ) {
        Booking b = mock(Booking.class);
        when(b.getId()).thenReturn(bookingId);
        when(b.getEventId()).thenReturn(eventId);
        when(b.getStatus()).thenReturn(status);
        when(b.getPaymentTxId()).thenReturn(paymentTxId);
        when(b.getCreatedAt()).thenReturn(createdAt);
        return b;
    }

    private static Booking mockBookingForDetail(
            long bookingId,
            long eventId,
            long userId,
            String paymentTxId,
            BookingStatus status,
            Instant createdAt
    ) {
        Booking b = mockBookingForList(bookingId, eventId, paymentTxId, status, createdAt);
        when(b.getUserId()).thenReturn(userId);
        return b;
    }
}
