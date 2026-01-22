package com.example.ticket.repository;

import com.example.ticket.domain.idempotency.HoldIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HoldIdempotencyRepository extends JpaRepository<HoldIdempotency, Long> {

    Optional<HoldIdempotency> findByUserIdAndEventIdAndIdempotencyKey(long userId, long eventId, String idempotencyKey);
}
