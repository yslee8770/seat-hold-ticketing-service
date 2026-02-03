package com.example.ticket.repository.dto;

public record BookingItemAgg(long bookingId, long itemCount, long totalPrice) {
    public static BookingItemAgg empty(long bookingId) {
        return new BookingItemAgg(bookingId, 0L, 0L);
    }
}