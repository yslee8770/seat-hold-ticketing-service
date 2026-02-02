package com.example.ticket.domain.idempotency;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "confirm_idempotencies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_confirm_idempotencies_payment_tx", columnNames = {"payment_tx_id"}),
                @UniqueConstraint(name = "uk_confirm_idempotencies_user_key", columnNames = {"user_id", "confirm_key"})
        },
        indexes = {
                @Index(name = "ix_confirm_idempotencies_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmIdempotency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "confirm_idempotency_id")
    private Long id;

    @Column(name = "payment_tx_id", nullable = false, length = 64)
    private String paymentTxId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "confirm_key", nullable = false, length = 100)
    private String confirmKey;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "hold_group_id", nullable = false, length = 64)
    private Long holdGroupId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    private ConfirmIdempotency(String paymentTxId, Long userId, String confirmKey,
                               Long eventId, Long holdGroupId, Long bookingId) {
        this.paymentTxId = Objects.requireNonNull(paymentTxId);
        this.userId = Objects.requireNonNull(userId);
        this.confirmKey = Objects.requireNonNull(confirmKey);
        this.eventId = Objects.requireNonNull(eventId);
        this.holdGroupId = Objects.requireNonNull(holdGroupId);
        this.bookingId = Objects.requireNonNull(bookingId);
    }

    public static ConfirmIdempotency create(String paymentTxId, Long userId, String confirmKey,
                                        Long eventId, Long holdToken, Long bookingId) {
        return new ConfirmIdempotency(paymentTxId, userId, confirmKey, eventId, holdToken, bookingId);
    }
}
