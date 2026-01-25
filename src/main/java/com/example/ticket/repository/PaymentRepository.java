package com.example.ticket.repository;


import com.example.ticket.domain.payment.PaymentTx;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentTx,String> {
    Optional<PaymentTx> getPaymentTxById(String paymentId);
}
