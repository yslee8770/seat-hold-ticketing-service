package com.example.ticket.api.admin;


import com.example.ticket.api.admin.dto.AdminPaymentDto.*;
import com.example.ticket.service.AdminPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;


    /**
     * (5-1) 결제 상태 결정(Stub)
     * POST /admin/payments
     * <p>
     * - PaymentTx 생성(또는 upsert)
     * - status 불변(결정 후 변경 금지) 규칙 적용
     */
    @PostMapping
    public AdminPaymentResponse decidePayment(@Valid @RequestBody AdminPaymentDecideRequest request) {
        return adminPaymentService.decide(request);
    }
}