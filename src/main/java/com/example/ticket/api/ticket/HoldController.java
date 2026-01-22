package com.example.ticket.api.ticket;

import com.example.ticket.api.ticket.dto.HoldDto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}")
public class HoldController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String USER_ID_HEADER = "X-User-Id"; // 임시 인증(나중에 JWT로 교체)

    // TODO: 너가 구현할 서비스/유스케이스 주입
    // private final HoldService holdService;

    public HoldController() {
        // this.holdService = holdService;
    }

    /**
     * (3-1) HOLD 획득(결제하기 버튼)
     * POST /api/events/{eventId}/holds
     *
     * Headers:
     *  - Idempotency-Key (required)
     *  - X-User-Id (temporary, until auth is implemented)
     */
    @PostMapping("/holds")
    public HoldCreateResponse createHold(
            @PathVariable long eventId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestHeader(USER_ID_HEADER) long userId,
            @Valid @RequestBody HoldCreateRequest request
    ) {
        // TODO:
        // HoldCreateResponse res = holdService.hold(userId, eventId, request, idempotencyKey);
        throw new UnsupportedOperationException("Not implemented");
    }
}
