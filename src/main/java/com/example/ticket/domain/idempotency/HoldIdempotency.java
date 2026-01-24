package com.example.ticket.domain.idempotency;


import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "hold_idempotencies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hold_idempotency_user_event_key", columnNames = {"user_id", "event_id", "idempotency_key"})
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

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    @Column(name = "seat_ids_key", nullable = false, length = 128)
    private String seatIdsKey;

    @Column(name = "hold_group_id", nullable = false, length = 64)
    private Long holdGroupId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private HoldIdempotency(Long userId, String idempotencyKey, Long eventId, String seatIdsKey,
                            Long holdGroupId, Instant expiresAt, Integer seatCount) {
        this.userId = Objects.requireNonNull(userId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.eventId = Objects.requireNonNull(eventId);
        this.seatIdsKey = Objects.requireNonNull(seatIdsKey);
        this.holdGroupId = Objects.requireNonNull(holdGroupId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.seatCount = Objects.requireNonNull(seatCount);
    }

    public static HoldIdempotency create(Long userId, String key, Long eventId, String seatIdsHash,
                                         Long holdToken, Instant expiresAt, Integer seatCount) {
        return new HoldIdempotency(userId, key, eventId, seatIdsHash, holdToken, expiresAt, seatCount);
    }
}
