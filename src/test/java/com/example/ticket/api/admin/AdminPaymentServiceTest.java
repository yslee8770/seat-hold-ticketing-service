package com.example.ticket.api.admin;

import com.example.ticket.api.admin.dto.AdminPaymentDto.AdminPaymentDecideRequest;
import com.example.ticket.api.admin.dto.AdminPaymentDto.AdminPaymentResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.payment.PaymentStatus;
import com.example.ticket.domain.payment.PaymentTx;
import com.example.ticket.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    Clock clock;
    AdminPaymentService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC);
        service = new AdminPaymentService(paymentRepository, clock);
    }

    @Test
    void decide_returns_existing_when_same_decision() {
        // given
        String paymentTxId = "ptx-1";
        Long userId = 1L;
        long amount = 1000L;
        PaymentStatus status = PaymentStatus.APPROVED;

        PaymentTx existing = PaymentTx.create(paymentTxId, userId, amount, status, Instant.parse("2026-01-01T00:00:00Z"));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(existing));

        // when
        AdminPaymentResponse res = service.decide(req(paymentTxId, userId, amount, status));

        // then
        assertThat(res.paymentTxId()).isEqualTo(paymentTxId);
        assertThat(res.userId()).isEqualTo(userId);
        assertThat(res.amount()).isEqualTo(amount);
        assertThat(res.status()).isEqualTo(status);

        verify(paymentRepository, never()).save(any(PaymentTx.class));
    }

    @Test
    void decide_throws_conflict_when_existing_differs() {
        // given
        String paymentTxId = "ptx-1";
        PaymentTx existing = PaymentTx.create(paymentTxId, 1L, 1000L, PaymentStatus.APPROVED, Instant.parse("2026-01-01T00:00:00Z"));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(existing));

        // when
        Throwable t = catchThrowable(() -> service.decide(req(paymentTxId, 1L, 2000L, PaymentStatus.APPROVED)));

        // then
        BusinessRuleViolationException ex = (BusinessRuleViolationException) t;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
        verify(paymentRepository, never()).save(any(PaymentTx.class));
    }

    @Test
    void decide_creates_when_not_exists_and_saves_once() {
        // given
        String paymentTxId = "ptx-2";
        Long userId = 2L;
        long amount = 3000L;
        PaymentStatus status = PaymentStatus.DECLINED;

        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTx.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        AdminPaymentResponse res = service.decide(req(paymentTxId, userId, amount, status));

        // then
        assertThat(res.paymentTxId()).isEqualTo(paymentTxId);
        assertThat(res.userId()).isEqualTo(userId);
        assertThat(res.amount()).isEqualTo(amount);
        assertThat(res.status()).isEqualTo(status);
        assertThat(res.decidedAt()).isEqualTo(Instant.now(clock));

        ArgumentCaptor<PaymentTx> captor = ArgumentCaptor.forClass(PaymentTx.class);
        verify(paymentRepository, times(1)).save(captor.capture());
        PaymentTx saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(paymentTxId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAmount()).isEqualTo(amount);
        assertThat(saved.getStatus()).isEqualTo(status);
        assertThat(saved.getDecidedAt()).isEqualTo(Instant.now(clock));
    }

    @Test
    void decide_race_save_duplicate_then_returns_existing_when_same() {
        // given
        String paymentTxId = "ptx-3";
        Long userId = 3L;
        long amount = 4000L;
        PaymentStatus status = PaymentStatus.TIMEOUT;

        PaymentTx existingAfterRace = PaymentTx.create(paymentTxId, userId, amount, status, Instant.parse("2026-01-25T00:00:00Z"));

        when(paymentRepository.getPaymentTxById(paymentTxId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));

        when(paymentRepository.save(any(PaymentTx.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when
        AdminPaymentResponse res = service.decide(req(paymentTxId, userId, amount, status));

        // then
        assertThat(res.paymentTxId()).isEqualTo(paymentTxId);
        assertThat(res.userId()).isEqualTo(userId);
        assertThat(res.amount()).isEqualTo(amount);
        assertThat(res.status()).isEqualTo(status);

        verify(paymentRepository, times(1)).save(any(PaymentTx.class));
        verify(paymentRepository, times(2)).getPaymentTxById(paymentTxId);
    }

    @Test
    void decide_race_save_duplicate_then_conflict_when_existing_differs() {
        // given
        String paymentTxId = "ptx-4";
        Long userId = 4L;

        PaymentTx existingAfterRace = PaymentTx.create(paymentTxId, userId, 9999L, PaymentStatus.APPROVED, Instant.parse("2026-01-25T00:00:00Z"));

        when(paymentRepository.getPaymentTxById(paymentTxId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));

        when(paymentRepository.save(any(PaymentTx.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when
        Throwable t = catchThrowable(() -> service.decide(req(paymentTxId, userId, 1000L, PaymentStatus.APPROVED)));

        // then
        BusinessRuleViolationException ex = (BusinessRuleViolationException) t;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void decide_race_save_duplicate_then_rethrow_when_reread_empty() {
        // given
        String paymentTxId = "ptx-5";

        when(paymentRepository.getPaymentTxById(paymentTxId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(PaymentTx.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // then
        assertThatThrownBy(() -> service.decide(req(paymentTxId, 5L, 1000L, PaymentStatus.APPROVED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AdminPaymentDecideRequest req(String paymentTxId, Long userId, long amount, PaymentStatus status) {
        return new AdminPaymentDecideRequest(paymentTxId, userId, amount, status, Instant.now());
    }
}