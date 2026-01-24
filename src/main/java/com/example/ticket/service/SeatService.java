package com.example.ticket.service;

import com.example.ticket.api.ticket.dto.SeatQueryDto.SeatListResponse;
import com.example.ticket.domain.seat.Seat;
import com.example.ticket.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public SeatListResponse getSeats(long eventId) {
        List<Seat> seats = seatRepository.findAllByEventIdOrderByZoneCodeAscSeatNoAsc(eventId);
        return SeatListResponse.from(eventId, seats);
    }
}
