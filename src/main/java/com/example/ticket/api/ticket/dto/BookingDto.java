package com.example.ticket.api.ticket.dto;


import com.example.ticket.domain.booking.Booking;
import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.domain.booking.BookingStatus;
import com.example.ticket.repository.dto.BookingItemAgg;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@NoArgsConstructor
public final class BookingDto {


    public record BookingListResponse(
            List<BookingSummaryDto> bookings
    ) {
        public static BookingListResponse from(List<BookingSummaryDto> bookings) {
            return new BookingListResponse(bookings);
        }
    }

    public record BookingSummaryDto(
            long bookingId,
            long eventId,
            BookingStatus status,
            String paymentTxId,
            Instant createdAt,
            long itemCount,
            long totalAmount
    ) {
        public static BookingSummaryDto from(Booking booking, long itemCount, long totalAmount) {
            return new BookingSummaryDto(
                    booking.getId(),
                    booking.getEventId(),
                    booking.getStatus(),
                    booking.getPaymentTxId(),
                    booking.getCreatedAt(),
                    itemCount,
                    totalAmount
            );
        }
    }

    public record BookingDetailResponse(
            long bookingId,
            long eventId,
            long userId,
            BookingStatus status,
            String paymentTxId,
            Instant createdAt,
            long totalAmount,
            List<BookingItemDto> items
    ) {
        public static BookingDetailResponse from(Booking booking, long totalAmount, List<BookingItemDto> items) {
            return new BookingDetailResponse(
                    booking.getId(),
                    booking.getEventId(),
                    booking.getUserId(),
                    booking.getStatus(),
                    booking.getPaymentTxId(),
                    booking.getCreatedAt(),
                    totalAmount,
                    items
            );

        }
    }

    public record BookingItemDto(
            long seatId,
            long price
    ) {
        public static BookingItemDto from(BookingItem bookingItem) {
            return new BookingItemDto(
                    bookingItem.getSeatId(),
                    bookingItem.getPrice()
            );
        }
    }
}