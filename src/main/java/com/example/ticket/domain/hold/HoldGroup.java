package com.example.ticket.domain.hold;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "hold_groups",
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
    @Column(name = "hold_group_id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;


    private HoldGroup(Long userId) {
        this.userId = userId;
    }


    public static HoldGroup create(Long userId) {
        return new HoldGroup(userId);
    }

}
