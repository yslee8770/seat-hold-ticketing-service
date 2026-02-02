package com.example.ticket.repository;

import com.example.ticket.domain.idempotency.ConfirmIdempotency;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfirmIdempotencyRepository extends JpaRepository<ConfirmIdempotency, Long> {
    Optional<ConfirmIdempotency> findByPaymentTxId(@NotBlank String s);

    Optional<ConfirmIdempotency> findByUserIdAndConfirmKey(long userId, String confirmIdempotencyKey);
}
