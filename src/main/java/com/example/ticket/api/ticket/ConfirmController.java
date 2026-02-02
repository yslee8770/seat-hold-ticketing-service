package com.example.ticket.api.ticket;


import com.example.ticket.api.ticket.dto.ConfirmDto.*;
import com.example.ticket.service.ConfirmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class ConfirmController {

    // 임시 인증(나중에 JWT로 교체)
    public static final String USER_ID_HEADER = "X-User-Id";

    private final ConfirmService confirmService;


    /**
     * (6-1) Confirm(확정)
     * POST /api/events/{eventId}/confirms
     *
     * Header:
     *  - X-User-Id (temporary)
     */
    @PostMapping("/confirms")
    public ConfirmResponse confirm(
            @PathVariable long eventId,
            @RequestHeader(USER_ID_HEADER) long userId,
            @Valid @RequestBody ConfirmRequest request
    ) {
        return confirmService.confirm(userId, eventId, request);
    }
}