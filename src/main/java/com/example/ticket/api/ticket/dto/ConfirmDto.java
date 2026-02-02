package com.example.ticket.api.ticket.dto;

import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.domain.idempotency.ConfirmIdempotency;
import com.example.ticket.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfirmDto {

    public record ConfirmRequest(
            @NotNull Long holdGroupId,
            @NotBlank String paymentTxId,
            @NotBlank String confirmIdempotencyKey,
            @NotNull @PositiveOrZero Long amount
    ) {
    }

    public record ConfirmResponse(
            long bookingId,
            long eventId,
            long userId,
            PaymentStatus status,
            String paymentTxId,
            long totalAmount,
            List<BookingItemDto> items
    ) {
        public static ConfirmResponse from(ConfirmIdempotency confirmIdempotency, PaymentStatus status, long amount, List<BookingItemDto> items) {
            return new ConfirmResponse(
                    confirmIdempotency.getBookingId(),
                    confirmIdempotency.getEventId(),
                    confirmIdempotency.getUserId(),
                    status,
                    confirmIdempotency.getPaymentTxId(),
                    amount,
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