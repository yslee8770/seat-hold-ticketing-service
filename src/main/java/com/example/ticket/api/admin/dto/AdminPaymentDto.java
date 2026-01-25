package com.example.ticket.api.admin.dto;

import com.example.ticket.domain.payment.PaymentStatus;
import com.example.ticket.domain.payment.PaymentTx;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminPaymentDto {

    public record AdminPaymentDecideRequest(
            @NotBlank String paymentTxId,
            @NotNull Long userId,
            @PositiveOrZero long amount,
            @NotNull PaymentStatus status,      // APPROVED | DECLINED | TIMEOUT
            Instant decidedAt            // null이면 서버 now
    ) {
    }

    public record AdminPaymentResponse(
            String paymentTxId,
            long userId,
            long amount,
            PaymentStatus status,
            Instant decidedAt
    ) {
        public static AdminPaymentResponse from(PaymentTx paymentTx) {
            return new AdminPaymentResponse(
                    paymentTx.getId(),
                    paymentTx.getUserId(),
                    paymentTx.getAmount(),
                    paymentTx.getStatus(),
                    paymentTx.getDecidedAt()
            );
        }
    }
}
