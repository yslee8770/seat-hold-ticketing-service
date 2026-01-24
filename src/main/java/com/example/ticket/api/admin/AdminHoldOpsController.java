package com.example.ticket.api.admin;

import com.example.ticket.api.ticket.dto.AdminExpireSweepDto.*;
import com.example.ticket.service.ExpireSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/holds")
@RequiredArgsConstructor
public class AdminHoldOpsController {

     private final ExpireSweepService expireSweepService;

    /**
     * (4-1) 만료 스윕 강제 실행(운영 트리거)
     * POST /admin/holds/sweep-expired
     */
    @PostMapping("/sweep-expired")
    public AdminExpireSweepResponse sweepExpired() {
        return expireSweepService.sweepExpired();
    }
}