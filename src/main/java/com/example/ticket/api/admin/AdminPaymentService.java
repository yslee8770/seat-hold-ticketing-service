package com.example.ticket.api.admin;

import com.example.ticket.api.admin.dto.AdminPaymentDto.*;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.payment.PaymentTx;
import com.example.ticket.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Transactional
    public AdminPaymentResponse decide(AdminPaymentDecideRequest request) {
        return paymentRepository.getPaymentTxById(request.paymentTxId())
                .map(existing -> {
                    if (sameDecision(existing, request)) {
                        return AdminPaymentResponse.from(existing);
                    }
                    throw new BusinessRuleViolationException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
                })
                .orElseGet(() -> createWithRaceHandling(request));
    }

    @Transactional
    public AdminPaymentResponse createWithRaceHandling(AdminPaymentDecideRequest request) {
        try {
            PaymentTx saved = paymentRepository.save(PaymentTx.create(
                    request.paymentTxId(),
                    request.userId(),
                    request.amount(),
                    request.status(),
                    HoldTimes.now(clock)
            ));
            return AdminPaymentResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.getPaymentTxById(request.paymentTxId())
                    .map(existing -> {
                        if (sameDecision(existing, request)) {
                            return AdminPaymentResponse.from(existing);
                        }
                        throw new BusinessRuleViolationException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
                    })
                    .orElseThrow(() -> e);
        }
    }

    private boolean sameDecision(PaymentTx tx, AdminPaymentDecideRequest req) {
        return tx.getUserId().equals(req.userId())
                && tx.getAmount() == req.amount()
                && tx.getStatus() == req.status();
    }
}
