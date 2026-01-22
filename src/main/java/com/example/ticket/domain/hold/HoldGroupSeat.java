package com.example.ticket.domain.hold;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "hold_group_seats",
        indexes = {
                @Index(name = "ix_hold_group_seats_group", columnList = "hold_group_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroupSeat {

    @Id
    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "hold_group_id", nullable = false)
    private UUID holdGroupId; // HoldGroup PK가 UUID면

    private HoldGroupSeat(UUID holdGroupId, Long seatId) {
        this.holdGroupId = Objects.requireNonNull(holdGroupId);
        this.seatId = Objects.requireNonNull(seatId);
    }

    public static HoldGroupSeat create(UUID holdGroupId, Long seatId) {
        return new HoldGroupSeat(holdGroupId, seatId);
    }
}