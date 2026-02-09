package com.example.ticket.domain.seat;

import com.example.ticket.domain.BaseTimeEntity;
import com.example.ticket.domain.event.SeatStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seats_event_zone_seatno",
                        columnNames = {"event_id", "zone_code", "seat_no"}
                )
        },
        indexes = {
                @Index(name = "ix_seats_event", columnList = "event_id"),
                @Index(name = "ix_seats_event_status", columnList = "event_id, status"),
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "zone_code", nullable = false, length = 50)
    private String zoneCode;

    @Column(name = "seat_no", nullable = false, length = 50)
    private String seatNo;

    @Column(name = "price", nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SeatStatus status;

    private Seat(Long eventId, String zoneCode, String seatNo, long price) {
        this.eventId = Objects.requireNonNull(eventId);
        this.zoneCode = Objects.requireNonNull(zoneCode);
        this.seatNo = Objects.requireNonNull(seatNo);
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public static Seat create(Long eventId, String zoneCode, String seatNo, long price) {
        return new Seat(eventId, zoneCode, seatNo, price);
    }

}
