package com.example.ticket.repository;

import com.example.ticket.domain.hold.HoldGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;


public interface HoldGroupRepository extends JpaRepository<HoldGroup, Long> {
    void deleteAllByExpiresAtLessThanEqual(Instant now);

    @Query("""
            select hg
            from HoldGroup hg
            where hg.id = :holdGroupId
              and hg.userId = :userId
              and hg.expiresAt > :now
            """)
    Optional<HoldGroup> findValidHoldGroup(long holdGroupId, long userId, Instant now);
}
