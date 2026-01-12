package com.example.ticket.api.admin;

import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventCreateRequest;
import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventResponse;
import com.example.ticket.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    // private final AdminEventService adminEventService;


    /**
     * 이벤트 생성(DRAFT)
     * POST /admin/events
     */
    @PostMapping
    public ApiResponse<AdminEventResponse> createEvent(@Valid @RequestBody AdminEventCreateRequest request) {
        // TODO: AdminEventResponse res = adminEventService.createDraft(request);
        throw new UnsupportedOperationException("Not implemented");
        // return Responses.ok(res);
    }

    /**
     * 이벤트 OPEN 전환
     * POST /admin/events/{eventId}/open
     */
    @PostMapping("/{eventId}/open")
    public ApiResponse<AdminEventResponse> openEvent(@PathVariable long eventId) {
        // TODO: AdminEventResponse res = adminEventService.open(eventId);
        throw new UnsupportedOperationException("Not implemented");
        // return Responses.ok(res);
    }

    /**
     * 이벤트 CLOSED 전환
     * POST /admin/events/{eventId}/close
     */
    @PostMapping("/{eventId}/close")
    public ApiResponse<AdminEventResponse> closeEvent(@PathVariable long eventId) {
        // TODO: AdminEventResponse res = adminEventService.close(eventId);
        throw new UnsupportedOperationException("Not implemented");
        // return Responses.ok(res);
    }
}
