package com.example.ticket.service;

import com.example.ticket.api.admin.dto.AdminSeatDto.*;
import com.example.ticket.api.admin.dto.AdminSeatSummaryDto.*;
import com.example.ticket.common.BusinessRuleViolationException;
import com.example.ticket.common.ErrorCode;
import com.example.ticket.domain.event.Event;
import com.example.ticket.domain.event.EventStatus;
import com.example.ticket.domain.event.SeatStatus;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.EventRepository;
import com.example.ticket.repository.SeatRepository;
import com.example.ticket.repository.dto.SeatStatusCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSeatService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public AdminSeatBulkUpsertResponse bulkReplace(long eventId, AdminSeatBulkUpsertRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessRuleViolationException(ErrorCode.EVENT_NOT_DRAFT);
        }

        validateNoDuplicates(request.seats());
        seatRepository.deleteAllByEventId(eventId);

        List<Seat> seats = request.seats().stream()
                .map(it -> Seat.create(eventId, it.zoneCode(), it.seatNo(), it.price()))
                .toList();
        seatRepository.saveAll(seats);
        return AdminSeatBulkUpsertResponse.of(eventId, seats.size());
    }

    @Transactional(readOnly = true)
    public AdminSeatSummaryResponse getSummary(long eventId) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessRuleViolationException(ErrorCode.EVENT_NOT_FOUND));

        long available = 0L;
        long held = 0L;
        long sold = 0L;

        for (SeatStatusCount row : seatRepository.countByStatus(eventId)) {
            if (row.status() == SeatStatus.AVAILABLE) available = row.count();
            else if (row.status() == SeatStatus.HELD) held = row.count();
            else if (row.status() == SeatStatus.SOLD) sold = row.count();
        }
        return AdminSeatSummaryResponse.of(eventId, available, held, sold);
    }

    private void validateNoDuplicates(List<AdminSeatCreateDto> items) {
        HashSet<String> seen = new HashSet<>(items.size() * 2);
        for (AdminSeatCreateDto seatCreateDto : items) {
            String key = seatCreateDto.zoneCode() + "#" + seatCreateDto.seatNo();
            if (!seen.add(key)) {
                throw new BusinessRuleViolationException(ErrorCode.DUPLICATED_SEAT_IN_REQUEST);
            }
        }
    }
}
