package com.example.ticket.api.ticket;

import com.example.ticket.api.ticket.dto.BookingDto.*;
import com.example.ticket.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    // 임시 인증(나중에 JWT로 교체)
    public static final String USER_ID_HEADER = "X-User-Id";

    private final BookingService bookingService;


    /**
     * (7-1) 내 예매 내역 조회
     * GET /api/bookings?eventId={optional}
     */
    @GetMapping
    public BookingListResponse list(
            @RequestHeader(USER_ID_HEADER) long userId,
            @RequestParam(name = "eventId", required = false) Long eventId
    ) {
        return bookingService.list(userId, eventId);
    }

    /**
     * (7-2) 예매 상세 조회
     * GET /api/bookings/{bookingId}
     * <p>
     * Rule: 본인 소유 booking만 조회 가능
     */
    @GetMapping("/{bookingId}")
    public BookingDetailResponse detail(
            @RequestHeader(USER_ID_HEADER) long userId,
            @PathVariable long bookingId
    ) {

        return bookingService.detail(userId, bookingId);
    }
}

