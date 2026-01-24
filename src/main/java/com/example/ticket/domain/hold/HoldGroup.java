package com.example.ticket.domain.hold;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "hold_groups"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_group_id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private HoldGroup(Long userId, Instant expiresAt) {
        this.userId = userId;
    }

    public static HoldGroup create(Long userId, Instant expiresAt) {
        return new HoldGroup(userId, expiresAt);
    }

}
