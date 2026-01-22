package com.example.ticket.repository;

import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.dto.SeatStatusCount;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Seat s where s.eventId = :eventId")
    int deleteAllByEventId(@Param("eventId") long eventId);

    @Query("""
        select new com.example.ticket.repository.dto.SeatStatusCount(s.status, count(s))
        from Seat s
        where s.eventId = :eventId
        group by s.status
    """)
    List<SeatStatusCount> countByStatus(@Param("eventId") long eventId);

    List<Seat> findAllByEventIdOrderByZoneCodeAscSeatNoAsc(Long eventId);

    @Modifying
    @Query("""
    update Seat s
       set s.status = com.example.ticket.domain.event.SeatStatus.HELD,
           s.heldByUserId = :userId,
           s.holdUntil = :holdUntil
     where s.eventId = :eventId
       and s.id in :seatIds
       and s.status = com.example.ticket.domain.event.SeatStatus.AVAILABLE
""")
    int holdIfAllAvailable(long eventId, List<Long> seatIds, long userId, Instant holdUntil);
}
