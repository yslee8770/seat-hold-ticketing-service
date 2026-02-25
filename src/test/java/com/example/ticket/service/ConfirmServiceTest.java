package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.ConfirmDto.ConfirmRequest;
import com.example.ticket.api.ticket.dto.ConfirmDto.ConfirmResponse;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.booking.Booking;
import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.EventStatus;
import com.example.ticket.domain.hold.HoldGroup;
import com.example.ticket.domain.hold.HoldTimes;
import com.example.ticket.domain.idempotency.ConfirmIdempotency;
import com.example.ticket.domain.payment.PaymentStatus;
import com.example.ticket.domain.payment.PaymentTx;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmServiceTest {

    private static final String UK_CONFIRM_PAYMENT_TX = "uk_confirm_idempotencies_payment_tx";
    private static final String UK_CONFIRM_USER_KEY   = "uk_confirm_idempotencies_user_key";
    private static final String UK_BOOKINGS_PAYMENT_TX = "uk_bookings_payment_tx";
    private static final String UK_BOOKING_ITEMS_SEAT  = "uk_booking_items_seat";

    @Mock ConfirmIdempotencyRepository confirmIdempotencyRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock BookingItemRepository bookingItemRepository;
    @Mock BookingRepository bookingRepository;
    @Mock HoldGroupRepository holdGroupRepository;
    @Mock HoldGroupSeatRepository holdGroupSeatRepository;
    @Mock SeatRepository seatRepository;
    @Mock EventRepository eventRepository;

    private ConfirmService confirmService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-25T00:00:00Z"), ZoneOffset.UTC);
        confirmService = new ConfirmService(
                confirmIdempotencyRepository,
                paymentRepository,
                bookingItemRepository,
                bookingRepository,
                holdGroupRepository,
                holdGroupSeatRepository,
                seatRepository,
                clock,
                eventRepository
        );
    }


    private void stubEventOnSale(long eventId, Instant now) {
        Event event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.OPEN);
        when(event.getSalesOpenAt()).thenReturn(now.minusSeconds(1));
        when(event.getSalesCloseAt()).thenReturn(now.plusSeconds(60));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    }



    private void stubPayment(String paymentTxId, long amount, PaymentStatus status) {
        PaymentTx payment = mock(PaymentTx.class);
        when(payment.getAmount()).thenReturn(amount);
        when(payment.getStatus()).thenReturn(status);
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
    }

    private static DataIntegrityViolationException dataIntegrity(String constraintName) {
        ConstraintViolationException cve =
                new ConstraintViolationException("constraint violated", new SQLException("dup"), constraintName);
        return new DataIntegrityViolationException("DIE", cve);
    }


    @Test
    void confirm_whenIdempotencyExists_sameDecision_returnsSameResponse_withoutSideEffects() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        String paymentTxId = "p1";
        long amount = 1000L;
        long holdGroupId = 55L;
        String confirmKey = "ck1";

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        ConfirmIdempotency idem = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, eventId, holdGroupId, 999L
        );

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId))
                .thenReturn(Optional.of(idem));

        stubPayment(paymentTxId, amount, PaymentStatus.APPROVED);

        BookingItem bi1 = BookingItem.create(999L, 1L, 500L);
        BookingItem bi2 = BookingItem.create(999L, 2L, 500L);
        when(bookingItemRepository.findAllByBookingId(999L)).thenReturn(List.of(bi1, bi2));

        ConfirmResponse res = confirmService.confirm(userId, eventId, req);

        assertEquals(eventId, res.eventId());
        assertEquals(paymentTxId, res.paymentTxId());
        assertEquals(PaymentStatus.APPROVED, res.status());
        assertEquals(amount, res.totalAmount());
        assertEquals(2, res.items().size());

        verify(bookingRepository, never()).save(any());
        verify(bookingItemRepository, never()).saveAll(any());
        verify(seatRepository, never()).changeSeatsSoldByHold(anyLong(), anyLong(), anyLong(), any(), anyList());
        verify(holdGroupSeatRepository, never()).deleteHoldGroupSeats(anyLong(), anyLong());
        verify(holdGroupRepository, never()).delete(any());
        verify(confirmIdempotencyRepository, never()).save(any());
    }

    @Test
    void confirm_whenIdempotencyExists_butDifferentDecision_throwsIdempotencyConflict() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";
        long amount = 1000L;

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        ConfirmIdempotency existing = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, eventId, 999L, 999L
        );

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId))
                .thenReturn(Optional.of(existing));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.CONFIRM_IDEMPOTENCY_CONFLICT, ex.getErrorCode());

        verify(paymentRepository, never()).getPaymentTxById(anyString());
        verify(confirmIdempotencyRepository, never()).save(any());
    }

    @Test
    void confirm_whenPaymentDeclined_throwsPaymentDeclined() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";
        long amount = 1000L;

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        stubPaymentDeclined(paymentTxId);

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.PAYMENT_DECLINED, ex.getErrorCode());

        verify(paymentRepository).getPaymentTxById(paymentTxId);
    }


    @Test
    void confirm_whenAmountMismatch_throwsAmountMismatch() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, 999L);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        stubPayment(paymentTxId, 1000L, PaymentStatus.APPROVED);

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.AMOUNT_MISMATCH, ex.getErrorCode());
    }

    @Test
    void confirm_whenHoldTokenNotFound_throwsHoldTokenNotFound() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";
        long amount = 1000L;

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        stubPayment(paymentTxId, amount, PaymentStatus.APPROVED);

        when(holdGroupRepository.findValidHoldGroup(holdGroupId, userId, eventId, now)).thenReturn(Optional.empty());

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.HOLD_TOKEN_NOT_FOUND, ex.getErrorCode());

        verify(holdGroupSeatRepository, never()).findValidSeatIds(anyLong(), anyLong(), any());
        verify(seatRepository, never()).changeSeatsSoldByHold(anyLong(), anyLong(), anyLong(), any(), anyList());
    }

    @Test
    void confirm_whenHoldExpired_byNoValidSeats_throwsHoldExpired() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";
        long amount = 1000L;

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        PaymentTx payment = paymentWith(amount, PaymentStatus.APPROVED);
        stubPaymentRepoReturns(paymentTxId, payment);

        when(holdGroupRepository.findValidHoldGroup(holdGroupId, userId, eventId, now))
                .thenReturn(Optional.of(mock(HoldGroup.class)));

        when(holdGroupSeatRepository.findValidSeatIds(holdGroupId, eventId, now)).thenReturn(List.of());

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.HOLD_EXPIRED, ex.getErrorCode());

        verify(seatRepository, never()).changeSeatsSoldByHold(anyLong(), anyLong(), anyLong(), any(), anyList());
    }

    @Test
    void confirm_whenHoldExpired_byUpdateCountMismatch_throwsHoldExpired() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        long holdGroupId = 55L;
        String paymentTxId = "p1";
        String confirmKey = "ck1";
        long amount = 1000L;

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        stubPayment(paymentTxId, amount, PaymentStatus.APPROVED);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(holdGroupId);
        when(holdGroupRepository.findValidHoldGroup(holdGroupId, userId, eventId, now)).thenReturn(Optional.of(hg));

        List<Long> seatIds = List.of(1L, 2L);
        when(holdGroupSeatRepository.findValidSeatIds(holdGroupId, eventId, now)).thenReturn(seatIds);

        when(seatRepository.changeSeatsSoldByHold(eq(eventId), eq(holdGroupId), eq(userId), eq(now), eq(seatIds)))
                .thenReturn(1);

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirm(userId, eventId, req)
        );
        assertEquals(ErrorCode.HOLD_EXPIRED, ex.getErrorCode());

        verify(seatRepository, never()).findAllById(anyList());
        verify(bookingRepository, never()).save(any());
        verify(confirmIdempotencyRepository, never()).save(any());
    }

    @Test
    void confirm_success_happyPath() {
        long userId = 1L;
        long eventId = 10L;

        Instant now = HoldTimes.now(clock);
        stubEventOnSale(eventId, now);

        String paymentTxId = "p1";
        long amount = 1000L;
        long holdGroupId = 55L;
        String confirmKey = "ck1";

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, amount);

        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId)).thenReturn(Optional.empty());
        when(confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, confirmKey)).thenReturn(Optional.empty());

        stubPayment(paymentTxId, amount, PaymentStatus.APPROVED);

        HoldGroup hg = mock(HoldGroup.class);
        when(hg.getId()).thenReturn(holdGroupId);
        when(holdGroupRepository.findValidHoldGroup(holdGroupId, userId, eventId, now)).thenReturn(Optional.of(hg));

        List<Long> seatIds = List.of(1L, 2L);
        when(holdGroupSeatRepository.findValidSeatIds(holdGroupId, eventId, now)).thenReturn(seatIds);

        when(seatRepository.changeSeatsSoldByHold(eq(eventId), eq(holdGroupId), eq(userId), eq(now), eq(seatIds)))
                .thenReturn(seatIds.size());

        Seat s1 = mock(Seat.class);
        when(s1.getId()).thenReturn(1L);
        when(s1.getPrice()).thenReturn(500L);

        Seat s2 = mock(Seat.class);
        when(s2.getId()).thenReturn(2L);
        when(s2.getPrice()).thenReturn(500L);

        when(seatRepository.findAllById(seatIds)).thenReturn(List.of(s1, s2));

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(777L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        BookingItem bi1 = BookingItem.create(777L, 1L, 500L);
        BookingItem bi2 = BookingItem.create(777L, 2L, 500L);
        when(bookingItemRepository.saveAll(anyList())).thenReturn(List.of(bi1, bi2));

        when(holdGroupSeatRepository.deleteHoldGroupSeats(holdGroupId, eventId)).thenReturn(seatIds.size());

        ConfirmIdempotency idemSaved = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, eventId, holdGroupId, 777L
        );
        when(confirmIdempotencyRepository.save(any(ConfirmIdempotency.class))).thenReturn(idemSaved);

        ConfirmResponse res = confirmService.confirm(userId, eventId, req);

        assertEquals(eventId, res.eventId());
        assertEquals(paymentTxId, res.paymentTxId());
        assertEquals(PaymentStatus.APPROVED, res.status());
        assertEquals(amount, res.totalAmount());
        assertEquals(2, res.items().size());

        ArgumentCaptor<ConfirmIdempotency> idemCaptor = ArgumentCaptor.forClass(ConfirmIdempotency.class);
        verify(confirmIdempotencyRepository).save(idemCaptor.capture());
        assertEquals(777L, idemCaptor.getValue().getBookingId());

        verify(holdGroupRepository).delete(hg);
    }


    @Test
    void saveConfirmIdempotency_whenDuplicateKey_returnsExisting_ifSameDecision() {
        long userId = 1L;
        long eventId = 10L;

        String paymentTxId = "p1";
        long holdGroupId = 55L;
        String confirmKey = "ck1";

        ConfirmRequest req = ConfirmRequest.create(holdGroupId, paymentTxId, confirmKey, 4L);

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(777L);

        when(confirmIdempotencyRepository.save(any(ConfirmIdempotency.class)))
                .thenThrow(dataIntegrity(UK_CONFIRM_PAYMENT_TX));

        ConfirmIdempotency existing = ConfirmIdempotency.create(
                paymentTxId, userId, confirmKey, eventId, holdGroupId, 777L
        );
        when(confirmIdempotencyRepository.findByPaymentTxId(paymentTxId))
                .thenReturn(Optional.of(existing));

        ConfirmIdempotency result = confirmService.saveConfirmIdempotency(userId, eventId, req, booking);
        assertSame(existing, result);
    }

    @Test
    void confirmBooking_whenDuplicatePaymentTx_throwsBookingAlreadySaved() {
        long userId = 1L;
        long eventId = 10L;

        ConfirmRequest req = ConfirmRequest.create(55L, "p1", "ck1", 4L);

        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(dataIntegrity(UK_BOOKINGS_PAYMENT_TX));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirmBooking(userId, eventId, req)
        );
        assertEquals(ErrorCode.BOOKING_ALREADY_SAVED, ex.getErrorCode());
    }

    @Test
    void confirmBookingItems_whenDuplicateSeat_throwsBookingItemAlreadySaved() {
        Seat s1 = mock(Seat.class);
        when(s1.getId()).thenReturn(1L);
        when(s1.getPrice()).thenReturn(500L);

        when(bookingItemRepository.saveAll(anyList()))
                .thenThrow(dataIntegrity(UK_BOOKING_ITEMS_SEAT));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.confirmBookingItems(List.of(s1), 777L)
        );
        assertEquals(ErrorCode.BOOKING_ITEM_ALREADY_SAVED, ex.getErrorCode());
    }

    @Test
    void getPayment_PAYMENT_IDEMPOTENCY_CONFLICT(){
        stubPaymentNothing("1L");
        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.getPayment("1L", 777L)
        );
        assertEquals(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    void getPayment_PAYMENT_TIMEOUT(){
        stubPaymentTimeout("1L");
        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> confirmService.getPayment("1L", 777L)
        );
        assertEquals(ErrorCode.PAYMENT_TIMEOUT, ex.getErrorCode());
    }

    private void stubPaymentRepoReturns(String paymentTxId, PaymentTx payment) {
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
    }

    private PaymentTx paymentWith(long amount, PaymentStatus status) {
        PaymentTx payment = mock(PaymentTx.class);
        when(payment.getAmount()).thenReturn(amount);
        when(payment.getStatus()).thenReturn(status);
        return payment;
    }

    private void stubPaymentNothing(String paymentTxId) {
        when(paymentRepository.getPaymentTxById(paymentTxId))
                .thenReturn(Optional.empty());
    }

    private void stubPaymentDeclined(String paymentTxId) {
        PaymentTx payment = mock(PaymentTx.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.DECLINED);
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
    }


    private void stubPaymentTimeout(String paymentTxId) {
        PaymentTx payment = mock(PaymentTx.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.TIMEOUT);
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
    }

    private void stubPaymentApproved(String paymentTxId, long amount) {
        PaymentTx payment = mock(PaymentTx.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getAmount()).thenReturn(amount);
        when(paymentRepository.getPaymentTxById(paymentTxId)).thenReturn(Optional.of(payment));
    }
}
