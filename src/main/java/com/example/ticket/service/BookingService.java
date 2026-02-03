package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.BookingDto.*;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.common.exception.BusinessRuleViolationException;
import com.example.ticket.domain.booking.Booking;
import com.example.ticket.domain.booking.BookingItem;
import com.example.ticket.repository.BookingItemRepository;
import com.example.ticket.repository.BookingRepository;
import com.example.ticket.repository.dto.BookingItemAgg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    @Transactional(readOnly = true)
    public BookingListResponse list(long userId, Long eventId) {
        List<Booking> bookingList = getBookingList(userId, eventId);
        if (bookingList.isEmpty()) {
            return BookingListResponse.from(List.of());
        }

        List<Long> bookingIds = bookingList.stream().map(Booking::getId).toList();
        return BookingListResponse.from(getBookingSummaryList(bookingIds, bookingList));
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryDto> getBookingSummaryList(List<Long> bookingIds, List<Booking> bookingList) {
        var aggMap = bookingItemRepository.aggregateByBookingIds(bookingIds).stream()
                .collect(java.util.stream.Collectors.toMap(BookingItemAgg::bookingId, a -> a));

        return bookingList.stream()
                .map(b -> {
                    BookingItemAgg agg = aggMap.getOrDefault(b.getId(), BookingItemAgg.empty(b.getId()));
                    return BookingSummaryDto.from(b, agg.itemCount(), agg.totalPrice());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingList(long userId, Long eventId) {
        return (eventId == null)
                ? bookingRepository.findByUserId(userId)
                : bookingRepository.findByUserIdAndEventId(userId, eventId);
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse detail(long userId, long bookingId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.BOOKING_NOT_FOUND));

        List<BookingItem> items = bookingItemRepository.findAllByBookingId(booking.getId());
        return BookingDetailResponse.from(
                booking,
                items.stream().mapToLong(BookingItem::getPrice).sum(),
                items.stream()
                        .map(BookingItemDto::from)
                        .toList()
        );
    }
}
