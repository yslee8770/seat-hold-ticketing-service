package com.example.ticket.domain.hold;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "hold_group_seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hold_group_seats_seat_id_event_id", columnNames = {"event_id", "seat_id"})
        },
        indexes = {
                @Index(name = "ix_hold_group_seats_group", columnList = "hold_group_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroupSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_seat_id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "hold_group_id", nullable = false)
    private Long holdGroupId;

    public HoldGroupSeat(Long seatId, Long eventId, Instant expiresAt, Long holdGroupId) {
        this.eventId = eventId;
        this.seatId = seatId;
        this.expiresAt = expiresAt;
        this.holdGroupId = holdGroupId;
    }

    public static HoldGroupSeat create(Long seatId, Long eventId, Instant expiresAt, Long holdGroupId) {
        return new HoldGroupSeat(seatId, eventId, expiresAt, holdGroupId);
    }
}