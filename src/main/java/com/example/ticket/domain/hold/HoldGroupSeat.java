package com.example.ticket.domain.hold;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(
        name = "hold_group_seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hold_group_seats_seat", columnNames = {"seat_id"})
        },
        indexes = {
                @Index(name = "ix_hold_group_seats_group", columnList = "hold_group_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroupSeat {

    @EmbeddedId
    private Pk pk;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    private HoldGroupSeat(Long holdGroupId, Long seatId) {
        this.pk = new Pk(holdGroupId, seatId);
        this.seatId = Objects.requireNonNull(seatId);
    }

    public static HoldGroupSeat of(Long holdGroupId, Long seatId) {
        return new HoldGroupSeat(holdGroupId, seatId);
    }

    @Embeddable
    public static class Pk implements Serializable {
        @Column(name = "hold_group_id", nullable = false)
        private Long holdGroupId;

        @Column(name = "seat_id", nullable = false)
        private Long seatId;

        protected Pk() {}

        public Pk(Long holdGroupId, Long seatId) {
            this.holdGroupId = Objects.requireNonNull(holdGroupId);
            this.seatId = Objects.requireNonNull(seatId);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(holdGroupId, pk.holdGroupId) && Objects.equals(seatId, pk.seatId);
        }

        @Override public int hashCode() {
            return Objects.hash(holdGroupId, seatId);
        }
    }
}