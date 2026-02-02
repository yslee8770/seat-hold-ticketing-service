package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroupSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface HoldGroupSeatRepository extends JpaRepository<HoldGroupSeat, Long> {
    int deleteAllByExpiresAtLessThanEqual(Instant now);

    @Query("""
            select hgs.seatId
            from HoldGroupSeat hgs
            where hgs.holdGroupId = :holdGroupId
              and hgs.eventId = :eventId
              and hgs.expiresAt > :now
            order by hgs.seatId asc
            """)
    List<Long> findValidSeatIds(long holdGroupId, long eventId, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from HoldGroupSeat hgs
            where
            hgs.holdGroupId = :holdGroupId
            and hgs.eventId = :eventId
            """)
    int deleteHoldGroupSeats(long holdGroupId, long eventId);
}
