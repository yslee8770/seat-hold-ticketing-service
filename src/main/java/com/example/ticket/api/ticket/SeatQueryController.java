package com.example.ticket.api.ticket;

import com.example.ticket.api.ticket.dto.SeatQueryDto.*;
import com.example.ticket.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class SeatQueryController {

    private final SeatService seatService;

    /**
     * 좌석 목록 조회 ( 읽기 전용)
     * GET /api/events/{eventId}/seats
     *
     */
    @GetMapping("/seats")
    public SeatListResponse getSeats(@PathVariable long eventId) {
        return seatService.getSeats(eventId);
    }

}
