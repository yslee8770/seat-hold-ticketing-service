package com.example.ticket.repository;

import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.dto.SeatStatusCount;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update seats s
               set status = 'SOLD'
             where s.event_id = :eventId
               and s.seat_id in (:seatIds)
               and s.status = 'AVAILABLE'
               and exists (
                    select 1
                      from hold_group_seats hgs
                     where hgs.event_id = :eventId
                       and hgs.seat_id = s.seat_id
                       and hgs.hold_group_id = :holdGroupId
                       and hgs.expires_at > :now
               )
               and exists (
                    select 1
                      from hold_groups hg
                     where hg.hold_group_id = :holdGroupId
                       and hg.user_id = :userId
                       and hg.expires_at > :now
               )
            """, nativeQuery = true)
    int changeSeatsSoldByHold(long eventId,
                              long holdGroupId,
                              long userId,
                              Instant now,
                              List<Long> seatIds);

    long countByEventIdAndIdInAndStatus(long eventId, List<Long> seatIds, SeatStatus status);
}


