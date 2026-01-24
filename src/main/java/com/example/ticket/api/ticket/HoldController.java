package com.example.ticket.api.ticket;

import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateRequest;
import com.example.ticket.api.ticket.dto.HoldDto.HoldCreateResponse;
import com.example.ticket.service.HoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
public class HoldController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String USER_ID_HEADER = "X-User-Id";

    private final HoldService holdService;


    /**
     * (3-1) HOLD 획득(결제하기 버튼)
     * POST /api/events/{eventId}/holds
     *
     * Headers:
     * - Idempotency-Key (required)
     * - X-User-Id (temporary, until auth is implemented)
     */
    @PostMapping("/holds")
    public HoldCreateResponse createHold(
            @PathVariable long eventId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestHeader(USER_ID_HEADER) long userId,
            @Valid @RequestBody HoldCreateRequest request
    ) {

        return holdService.hold(userId, eventId, request, idempotencyKey);
    }
}
