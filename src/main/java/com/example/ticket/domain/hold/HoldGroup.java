package com.example.ticket.domain.hold;

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
        name = "hold_groups",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hold_groups_event_user_token", columnNames = {"event_id", "user_id", "hold_token"})
        },
        indexes = {
                @Index(name = "ix_hold_groups_user_event", columnList = "user_id, event_id"),
                @Index(name = "ix_hold_groups_expires_at", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_group_id")
    private Long id;

    @Column(name = "hold_token", nullable = false, length = 64)
    private String holdToken;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private HoldGroup(Long eventId, Long userId, Instant expiresAt) {
        this.holdToken = UUID.randomUUID().toString().replace("-", "");
        this.eventId = Objects.requireNonNull(eventId);
        this.userId = Objects.requireNonNull(userId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static HoldGroup create(Long eventId, Long userId, Instant expiresAt) {
        return new HoldGroup(eventId, userId, expiresAt);
    }
}
