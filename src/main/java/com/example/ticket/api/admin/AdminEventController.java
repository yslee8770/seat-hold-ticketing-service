package com.example.ticket.api.admin;

import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventCreateRequest;
import com.example.ticket.api.admin.dto.AdminEventDto.AdminEventResponse;
import com.example.ticket.common.ApiResponse;
import com.example.ticket.service.AdminEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService adminEventService;

    /**
     * 이벤트 생성(DRAFT)
     * POST /admin/events
     */
    @PostMapping
    public AdminEventResponse createEvent(@Valid @RequestBody AdminEventCreateRequest request) {
        return adminEventService.createDraft(request);
    }

    /**
     * 이벤트 OPEN 전환
     * POST /admin/events/{eventId}/open
     */
    @PostMapping("/{eventId}/open")
    public AdminEventResponse openEvent(@PathVariable long eventId) {
        return adminEventService.open(eventId);
    }

    /**
     * 이벤트 CLOSED 전환
     * POST /admin/events/{eventId}/close
     */
    @PostMapping("/{eventId}/close")
    public AdminEventResponse closeEvent(@PathVariable long eventId) {
        return adminEventService.close(eventId);
    }
}
