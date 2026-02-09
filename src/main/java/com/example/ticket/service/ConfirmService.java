package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.ConfirmDto.BookingItemDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmService {

    private final ConfirmIdempotencyRepository confirmIdempotencyRepository;
    private final PaymentRepository paymentRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingRepository bookingRepository;
    private final HoldGroupRepository holdGroupRepository;
    private final HoldGroupSeatRepository holdGroupSeatRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;
    private final EventRepository eventRepository;

    @Transactional
    public ConfirmResponse confirm(long userId, long eventId, ConfirmRequest request) {
        Instant now = HoldTimes.now(clock);
        validEvent(eventId, now);

        Optional<ConfirmIdempotency> confirmIdempotency =
                confirmIdempotencyRepository.findByPaymentTxId(request.paymentTxId())
                        .or(() -> confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, request.confirmIdempotencyKey()));

        if (confirmIdempotency.isPresent()) {
            if (sameDecision(userId, eventId, request, confirmIdempotency.get())) {
                PaymentTx payment = getPayment(request.paymentTxId(), request.amount());
                List<BookingItemDto> bookingItemsDto = bookingItemRepository.findAllByBookingId(confirmIdempotency.get().getBookingId())
                        .stream()
                        .map(BookingItemDto::from)
                        .toList();
                return ConfirmResponse.from(confirmIdempotency.get(), payment.getStatus(), payment.getAmount(), bookingItemsDto);
            }
            throw new BusinessRuleViolationException(ErrorCode.CONFIRM_IDEMPOTENCY_CONFLICT);
        }
        PaymentTx payment = getPayment(request.paymentTxId(), request.amount());

        HoldGroup holdGroup = holdGroupRepository.findValidHoldGroup(request.holdGroupId(), userId, now)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.HOLD_TOKEN_NOT_FOUND));
        List<Long> holdGroupSeatIds = getHoldSeatIds(eventId, request, now);

        List<Seat> changedSeats = changeSeatsSold(eventId, holdGroup.getId(), userId, now, holdGroupSeatIds);

        Booking savedBook = confirmBooking(userId, eventId, request);
        List<BookingItem> savedBookingItems = confirmBookingItems(changedSeats, savedBook.getId());
        deleteSoldHolds(eventId, holdGroup, holdGroupSeatIds.size());

        return ConfirmResponse.from(
                saveConfirmIdempotency(userId, eventId, request, savedBook),
                payment.getStatus(),
                payment.getAmount(),
                savedBookingItems
                        .stream()
                        .map(BookingItemDto::from)
                        .toList());
    }
    @Transactional(readOnly = true)
    public void validEvent(long eventId, Instant now) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getStatus().equals(EventStatus.OPEN) || event.getSalesOpenAt().isAfter(now) || !now.isBefore(event.getSalesCloseAt())) {
            throw new BusinessRuleViolationException(ErrorCode.EVENT_NOT_ON_SALE);
        }
    }
    @Transactional
    public void deleteSoldHolds(long eventId, HoldGroup holdGroup, int holdGroupSeatSize) {
        int holdGroupSeatsDeleteCount = holdGroupSeatRepository.deleteHoldGroupSeats(holdGroup.getId(), eventId);
        if (holdGroupSeatSize != holdGroupSeatsDeleteCount) {
            log.debug("holdGroupSeatSize: {}", holdGroupSeatSize);
            log.debug("holdGroupSeatsDeleteCount: {}", holdGroupSeatsDeleteCount);
        }
        holdGroupRepository.delete(holdGroup);
    }

    @Transactional
    public ConfirmIdempotency saveConfirmIdempotency(long userId, long eventId, ConfirmRequest request, Booking savedBook) {
        try {
            return confirmIdempotencyRepository.save(
                    ConfirmIdempotency.create(
                            request.paymentTxId(),
                            userId,
                            request.confirmIdempotencyKey(),
                            eventId,
                            request.holdGroupId(),
                            savedBook.getId()
                    )
            );
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uk_confirm_idempotencies_payment_tx")
                    || isConstraint(e, "uk_confirm_idempotencies_user_key")) {
                Optional<ConfirmIdempotency> confirmIdempotency =
                        confirmIdempotencyRepository.findByPaymentTxId(request.paymentTxId())
                                .or(() -> confirmIdempotencyRepository.findByUserIdAndConfirmKey(userId, request.confirmIdempotencyKey()));
                if (confirmIdempotency.isPresent()) {
                    if (sameDecision(userId, eventId, request, confirmIdempotency.get())) {
                        return confirmIdempotency.get();
                    }
                    throw new BusinessRuleViolationException(ErrorCode.CONFIRM_IDEMPOTENCY_CONFLICT);
                }
                throw new BusinessRuleViolationException(ErrorCode.CONFIRM_IDEMPOTENCY_CONFLICT);
            }
            throw e;
        }
    }

    @Transactional
    public List<BookingItem> confirmBookingItems(List<Seat> changedSeats, Long bookingId) {
        try {
            return bookingItemRepository.saveAll(
                    changedSeats.stream()
                            .map(seat -> BookingItem.create(
                                    bookingId,
                                    seat.getId(),
                                    seat.getPrice()
                            ))
                            .toList()
            );
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uk_booking_items_seat")) {
                throw new BusinessRuleViolationException(ErrorCode.BOOKING_ITEM_ALREADY_SAVED);
            }
            throw e;
        }
    }

    @Transactional
    public Booking confirmBooking(long userId, long eventId, ConfirmRequest request) {
        try {
            return bookingRepository.save(Booking.create(
                    eventId,
                    userId,
                    request.paymentTxId()
            ));
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uk_bookings_payment_tx")) {
                throw new BusinessRuleViolationException(ErrorCode.BOOKING_ALREADY_SAVED);
            }
            throw e;
        }
    }

    @Transactional
    public List<Seat> changeSeatsSold(long eventId, Long holdGroupId, long userId, Instant now, List<Long> holdSeatIds) {
        int count = seatRepository.changeSeatsSoldByHold(eventId, holdGroupId, userId, now, holdSeatIds);
        if (count != holdSeatIds.size()) {
            throw new BusinessRuleViolationException(ErrorCode.HOLD_EXPIRED);
        }
        return seatRepository.findAllById(holdSeatIds);
    }

    @Transactional
    public List<Long> getHoldSeatIds(long eventId, ConfirmRequest request, Instant now) {
        List<Long> seats = holdGroupSeatRepository.findValidSeatIds(request.holdGroupId(), eventId, now);
        if (seats.isEmpty()) {
            throw new BusinessRuleViolationException(ErrorCode.HOLD_EXPIRED);
        }
        return seats;
    }

    @Transactional
    public PaymentTx getPayment(String paymentTxId, Long requestAmount) {
        PaymentTx payment = paymentRepository.getPaymentTxById(paymentTxId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT));
        if (payment.getStatus() == PaymentStatus.DECLINED) {
            throw new BusinessRuleViolationException(ErrorCode.PAYMENT_DECLINED);
        }
        if (payment.getStatus() == PaymentStatus.TIMEOUT) {
            throw new BusinessRuleViolationException(ErrorCode.PAYMENT_TIMEOUT);
        }
        if (payment.getAmount() != requestAmount) {
            throw new BusinessRuleViolationException(ErrorCode.AMOUNT_MISMATCH);
        }
        return payment;
    }

    private boolean sameDecision(long userId, long eventId, ConfirmRequest req, ConfirmIdempotency idem) {
        return idem.getUserId().equals(userId)
                && idem.getEventId().equals(eventId)
                && idem.getHoldGroupId().equals(req.holdGroupId())
                && idem.getPaymentTxId().equals(req.paymentTxId())
                && idem.getConfirmKey().equals(req.confirmIdempotencyKey());
    }

    private boolean isConstraint(Throwable e, String constraintName) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return constraintName.equalsIgnoreCase(cve.getConstraintName());
            }
            cur = cur.getCause();
        }
        return false;
    }

}
