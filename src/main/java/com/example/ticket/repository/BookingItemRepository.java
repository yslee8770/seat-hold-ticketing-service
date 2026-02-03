package com.example.ticket.repository;

import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.repository.dto.BookingItemAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findAllByBookingId(Long bookingId);

    @Query("""
                select new com.example.ticket.repository.dto.BookingItemAgg(
                    bi.bookingId,
                    count(bi),
                    coalesce(sum(bi.price), 0)
                )
                from BookingItem bi
                where bi.bookingId in :bookingIds
                group by bi.bookingId
            """)
    List<BookingItemAgg> aggregateByBookingIds(List<Long> bookingIds);
}
