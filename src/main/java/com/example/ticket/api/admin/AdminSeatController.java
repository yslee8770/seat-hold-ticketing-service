package com.example.ticket.api.admin;


import com.example.ticket.api.admin.dto.AdminSeatDto.AdminSeatBulkUpsertRequest;
import com.example.ticket.api.admin.dto.AdminSeatDto.AdminSeatBulkUpsertResponse;
import com.example.ticket.api.admin.dto.AdminSeatSummaryDto.AdminSeatSummaryResponse;
import com.example.ticket.service.AdminSeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events/{eventId}/seats")
@RequiredArgsConstructor
public class AdminSeatController {

    private final AdminSeatService adminSeatService;


    /**
     * 좌석 일괄 생성/초기화 (DRAFT에서만 허용)
     * POST /admin/events/{eventId}/seats/bulk
     */
    @PostMapping("/bulk")
    public AdminSeatBulkUpsertResponse bulkUpsert(
            @PathVariable long eventId,
            @Valid @RequestBody AdminSeatBulkUpsertRequest request
    ) {
        return adminSeatService.bulkReplace(eventId, request);
    }

    /**
     * 이벤트별 좌석 현황 조회(AVAILABLE/HELD/SOLD 카운트)
     * GET /admin/events/{eventId}/seats/summary
     */
    @GetMapping("/summary")
    public AdminSeatSummaryResponse getSummary(@PathVariable long eventId) {
        return adminSeatService.getSummary(eventId);
    }
}
