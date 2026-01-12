package com.example.ticket.domain.idempotency;


import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "hold_idempotencies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hold_idempotencies_user_key", columnNames = {"user_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "ix_hold_idempotencies_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldIdempotency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_idempotency_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "seat_ids_hash", nullable = false, length = 128)
    private String seatIdsHash;

    @Column(name = "hold_token", nullable = false, length = 64)
    private String holdToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private HoldIdempotency(Long userId, String idempotencyKey, Long eventId, String seatIdsHash,
                            String holdToken, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.eventId = Objects.requireNonNull(eventId);
        this.seatIdsHash = Objects.requireNonNull(seatIdsHash);
        this.holdToken = Objects.requireNonNull(holdToken);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static HoldIdempotency of(Long userId, String key, Long eventId, String seatIdsHash,
                                     String holdToken, Instant expiresAt) {
        return new HoldIdempotency(userId, key, eventId, seatIdsHash, holdToken, expiresAt);
    }
}
