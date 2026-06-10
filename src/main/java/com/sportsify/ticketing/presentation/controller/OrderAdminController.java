package com.sportsify.ticketing.presentation.controller;

import com.sportsify.ticketing.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {
    private final OrderService orderService;

    @PostMapping("/complete-sync")
    public ResponseEntity<String> syncCompleteStuckOrders(
            @AuthenticationPrincipal Long memberId
    ) {
        int successCount = orderService.completeStuckOrders();
        return ResponseEntity.ok(String.format("수동 처리가 완료되었습니다. (성공: %d건)", successCount));
    }
}
