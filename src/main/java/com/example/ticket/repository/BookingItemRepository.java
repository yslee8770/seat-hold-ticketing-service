package com.example.ticket.repository;

import com.example.ticket.domain.booking.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findAllByBookingId(Long bookingId);
}
