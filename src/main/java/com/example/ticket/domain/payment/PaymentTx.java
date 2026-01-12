package com.example.ticket.domain.payment;


import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "payment_txs",
        indexes = {
                @Index(name = "ix_payment_txs_user", columnList = "user_id"),
                @Index(name = "ix_payment_txs_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTx extends BaseTimeEntity {

    @Id
    @Column(name = "payment_tx_id", length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private PaymentTx(String id, Long userId, long amount, PaymentStatus status, Instant decidedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.amount = amount;
        this.status = Objects.requireNonNull(status);
        this.decidedAt = decidedAt;
    }

    public static PaymentTx decided(String paymentTxId, Long userId, long amount, PaymentStatus status, Instant decidedAt) {
        return new PaymentTx(paymentTxId, userId, amount, status, decidedAt);
    }
}
