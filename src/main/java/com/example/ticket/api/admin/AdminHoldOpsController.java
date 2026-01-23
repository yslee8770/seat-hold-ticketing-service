package com.example.ticket.api.admin;

import com.example.ticket.api.ticket.dto.AdminExpireSweepDto.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/holds")
public class AdminHoldOpsController {

    // TODO:  구현할 서비스/유스케이스 주입
    // private final ExpireSweepService expireSweepService;

    public AdminHoldOpsController() {
        // this.expireSweepService = expireSweepService;
    }

    /**
     * (4-1) 만료 스윕 강제 실행(운영 트리거)
     * POST /admin/holds/sweep-expired
     */
    @PostMapping("/sweep-expired")
    public AdminExpireSweepResponse sweepExpired() {
        // AdminExpireSweepResponse res = expireSweepService.sweepExpired();
        throw new UnsupportedOperationException("Not implemented");
    }
}