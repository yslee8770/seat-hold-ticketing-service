package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.ConfirmDto.ConfirmRequest;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.booking.Booking;
import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.idempotency.ConfirmIdempotency;
import com.example.ticket.domain.payment.PaymentStatus;
import com.example.ticket.domain.payment.PaymentTx;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmServiceTest {

    @Mock
    ConfirmIdempotencyRepository confirmIdempotencyRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    BookingItemRepository bookingItemRepository;
    @Mock
    BookingRepository bookingRepository;
    @Mock
    HoldGroupRepository holdGroupRepository;
    @Mock
    HoldGroupSeatRepository holdGroupSeatRepository;
    @Mock
    SeatRepository seatRepository;

    Clock clock;

    @InjectMocks
    ConfirmService confirmService;

    @BeforeEach
    void setUp() {
        Instant fixed = Instant.parse("2026-02-02T00:00:00Z");
        this.clock = Clock.fixed(fixed, ZoneOffset.UTC);

        confirmService = new ConfirmService(
                confirmIdempotencyRepository,
                paymentRepository,
                bookingItemRepository,
                bookingRepository,
                holdGroupRepository,
                holdGroupSeatRepository,
                seatRepository,
                clock
        );
    }

    @Test
    void confirm_idempotency_hit_sameDecision_returns_existing_response() {
        long userId = 1L;
        long eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        String confirmKey = "confirm-key-1";
        long amount = 1000L;

        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, confirmKey, amount);

        ConfirmIdempotency idem = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, eventId, holdGroupId, 777L
        );
        PaymentTx payment = PaymentTx.create(paymentTxId, userId, amount, PaymentStatus.APPROVED, Instant.now(clock));

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.of(idem));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
        when(bookingItemRepository.findAllByBookingId(777L))
                .thenReturn(List.of(BookingItem.create(777L, 1L, 500L), BookingItem.create(777L, 2L, 500L)));

        var res = confirmService.confirm(userId, eventId, req);

        assertThat(res.bookingId()).isEqualTo(777L);
        assertThat(res.eventId()).isEqualTo(eventId);
        assertThat(res.userId()).isEqualTo(userId);
        assertThat(res.paymentTxId()).isEqualTo(paymentTxId);
        assertThat(res.totalAmount()).isEqualTo(amount);
        assertThat(res.items()).hasSize(2);

        verify(holdGroupRepository, never()).findValidHoldGroup(anyLong(), anyLong(), any());
        verify(seatRepository, never()).changeSeatsSoldByHold(anyLong(), anyLong(), anyLong(), any(), anyList());
        verify(bookingRepository, never()).save(any());
        verify(confirmIdempotencyRepository, never()).save(any());
    }

    @Test
    void confirm_idempotency_hit_but_differentDecision_throws_conflict() {
        long userId = 1L;
        long eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        String confirmKey = "confirm-key-1";
        long amount = 1000L;

        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, confirmKey, amount);

        ConfirmIdempotency idem = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, 999L, holdGroupId, 777L
        );

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.of(idem));

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFIRM_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void payment_not_found_throws_payment_idempotency_conflict() {
        long userId = 1L, eventId = 10L;
        ConfirmRequest req = new ConfirmRequest(99L, "ptx-missing", "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.getPaymentTxById("ptx-missing")).thenReturn(Optional.empty());

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void payment_declined_throws_payment_declined() {
        long userId = 1L, eventId = 10L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(99L, paymentTxId, "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx declined = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.DECLINED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(declined));

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED);
    }

    @Test
    void payment_timeout_throws_payment_timeout() {
        long userId = 1L, eventId = 10L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(99L, paymentTxId, "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx timeout = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.TIMEOUT, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(timeout));

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_TIMEOUT);
    }

    @Test
    void payment_amount_mismatch_throws_amount_mismatch() {
        long userId = 1L, eventId = 10L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(99L, paymentTxId, "k", 999L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx approved = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.APPROVED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(approved));

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    void hold_group_not_found_throws_hold_token_not_found() {
        long userId = 1L, eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx approved = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.APPROVED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(approved));

        when(holdGroupRepository.findValidHoldGroup(eq(holdGroupId), eq(userId), any()))
                .thenReturn(Optional.empty());

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HOLD_TOKEN_NOT_FOUND);
    }

    @Test
    void hold_seats_empty_throws_hold_expired() {
        long userId = 1L, eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx approved = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.APPROVED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(approved));

        HoldGroup hg = HoldGroup.create(userId, Instant.now(clock).plusSeconds(10));
        when(holdGroupRepository.findValidHoldGroup(eq(holdGroupId), eq(userId), any()))
                .thenReturn(Optional.of(hg));

        when(holdGroupSeatRepository.findValidSeatIds(eq(holdGroupId), eq(eventId), any()))
                .thenReturn(List.of());

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HOLD_EXPIRED);
    }


    @Test
    void seat_update_count_mismatch_throws_hold_expired() {
        long userId = 1L, eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, "k", 100L);

        when(confirmIdempotencyRepository.findByPaymentTxId(anyString())).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(anyLong(), anyString())).thenReturn(Optional.empty());

        PaymentTx approved = PaymentTx.create(paymentTxId, userId, 100L, PaymentStatus.APPROVED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(approved));

        HoldGroup hg = HoldGroup.create(userId, Instant.now(clock).plusSeconds(10));
        HoldGroup spyHg = spy(hg);
        doReturn(holdGroupId).when(spyHg).getId();
        when(holdGroupRepository.findValidHoldGroup(eq(holdGroupId), eq(userId), any()))
                .thenReturn(Optional.of(spyHg));

        List<Long> seatIds = List.of(1L, 2L);
        when(holdGroupSeatRepository.findValidSeatIds(eq(holdGroupId), eq(eventId), any()))
                .thenReturn(seatIds);

        when(seatRepository.changeSeatsSoldByHold(eq(eventId), eq(holdGroupId), eq(userId), any(), eq(seatIds)))
                .thenReturn(1);

        BusinessRuleViolationException ex = catchThrowableOfType(
                () -> confirmService.confirm(userId, eventId, req),
                BusinessRuleViolationException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HOLD_EXPIRED);
    }

    @Test
    void save_confirm_idempotency_race_then_refetch_sameDecision_returns_existing() {
        long userId = 1L, eventId = 10L;
        long holdGroupId = 99L;
        String paymentTxId = "ptx-1";
        String confirmKey = "k";
        long amount = 100L;

        ConfirmRequest req = new ConfirmRequest(holdGroupId, paymentTxId, confirmKey, amount);

        ConfirmIdempotency existing =
                ConfirmIdempotency.create(paymentTxId, userId, confirmKey, eventId, holdGroupId, 777L);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey))
                .thenReturn(Optional.empty());

        PaymentTx approved = PaymentTx.create(paymentTxId, userId, amount, PaymentStatus.APPROVED, Instant.now(clock));
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(approved));

        HoldGroup hg = HoldGroup.create(userId, Instant.now(clock).plusSeconds(10));
        HoldGroup spyHg = spy(hg);
        doReturn(holdGroupId).when(spyHg).getId();

        when(holdGroupRepository.findValidHoldGroup(eq(holdGroupId), eq(userId), any()))
                .thenReturn(Optional.of(spyHg));

        List<Long> seatIds = List.of(1L);
        when(holdGroupSeatRepository.findValidSeatIds(eq(holdGroupId), eq(eventId), any()))
                .thenReturn(seatIds);

        when(seatRepository.changeSeatsSoldByHold(eq(eventId), eq(holdGroupId), eq(userId), any(), eq(seatIds)))
                .thenReturn(1);

        Seat seat = mock(Seat.class);
        when(seat.getId()).thenReturn(1L);
        when(seat.getPrice()).thenReturn(100L);
        when(seatRepository.findAllById(seatIds)).thenReturn(List.of(seat));

        Booking booking = Booking.create(eventId, userId, paymentTxId);
        Booking bookingSpy = spy(booking);
        doReturn(777L).when(bookingSpy).getId();
        when(bookingRepository.save(any())).thenReturn(bookingSpy);

        when(bookingItemRepository.saveAll(anyList()))
                .thenReturn(List.of(BookingItem.create(777L, 1L, 100L)));

        when(holdGroupSeatRepository.deleteHoldGroupSeats(anyLong(), anyLong())).thenReturn(1);

        when(confirmIdempotencyRepository.save(any()))
                .thenThrow(duplicateKey("uk_confirm_idempotencies_payment_tx"));

        var res = confirmService.confirm(userId, eventId, req);

        assertThat(res.bookingId()).isEqualTo(777L);
        verify(confirmIdempotencyRepository, times(1)).save(any());
        verify(confirmIdempotencyRepository, times(2)).findByPaymentTxId(paymentTxId);
    }

    private static DataIntegrityViolationException duplicateKey(String constraintName) {
        ConstraintViolationException cve =
                new ConstraintViolationException("dup", new SQLException("dup"), constraintName);
        return new DataIntegrityViolationException("dup", cve);
    }

}
