package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface HoldGroupRepository extends JpaRepository<HoldGroup, Long> {
    void deleteAllByExpiresAtLessThanEqual(Instant now);

    @Query("""
                select hg
                from HoldGroup hg
                where hg.id = :holdGroupId
                  and hg.userId = :userId
                  and hg.eventId = :eventId
                  and hg.expiresAt > :now
            """)
    Optional<HoldGroup> findValidHoldGroup(long holdGroupId, long userId, long eventId, Instant now);

    @Query("""
            select hg.id
            from HoldGroup hg
            where hg.userId = :userId
              and hg.eventId = :eventId
              and hg.expiresAt > :now
            """)
    List<Long> findActiveIds(@Param("userId") long userId,
                             @Param("eventId") long eventId,
                             @Param("now") Instant now);
}
