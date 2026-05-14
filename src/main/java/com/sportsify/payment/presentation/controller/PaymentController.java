package com.sportsify.payment.presentation.controller;

import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.CreatePaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid CreatePaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.createPayment(memberId, request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestBody @Valid ConfirmPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid CancelPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.cancelPayment(paymentId, memberId, request));
    }
}
