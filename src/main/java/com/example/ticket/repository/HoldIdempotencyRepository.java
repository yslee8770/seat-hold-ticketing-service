package com.example.ticket.repository;

import com.example.ticket.domain.idempotency.HoldIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface HoldIdempotencyRepository extends JpaRepository<HoldIdempotency, Long> {


    Optional<HoldIdempotency> findByUserIdAndEventIdAndIdempotencyKey(long userId, long eventId, String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                delete from HoldIdempotency h
                where h.userId = :userId
                  and h.eventId = :eventId
                  and h.idempotencyKey = :idempotencyKey
                  and h.holdGroupId is null
                  and h.seatCount is null
                  and h.expiresAt <= :now
            """)
    int deleteStaleInProgress(
            @Param("userId") long userId,
            @Param("eventId") long eventId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("now") Instant now
    );
}

