package com.example.ticket.domain.booking;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bookings_payment_tx", columnNames = {"payment_tx_id"})
        },
        indexes = {
                @Index(name = "ix_bookings_user_event", columnList = "user_id, event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "payment_tx_id", nullable = false, length = 64)
    private String paymentTxId;

    private Booking(Long eventId, Long userId, String paymentTxId) {
        this.eventId = Objects.requireNonNull(eventId);
        this.userId = Objects.requireNonNull(userId);
        this.paymentTxId = Objects.requireNonNull(paymentTxId);
        this.status = BookingStatus.CONFIRMED;
    }

    public static Booking confirmed(Long eventId, Long userId, String paymentTxId) {
        return new Booking(eventId, userId, paymentTxId);
    }
}

