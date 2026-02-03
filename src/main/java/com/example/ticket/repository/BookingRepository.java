package com.example.ticket.repository;

import com.example.ticket.domain.booking.Booking;
import com.example.ticket.repository.dto.BookingItemAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdAndEventId(long userId, Long eventId);

    List<Booking> findByUserId(long userId);

    Optional<Booking> findByIdAndUserId(long bookingId, long userId);
}
