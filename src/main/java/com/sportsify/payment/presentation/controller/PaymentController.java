package com.sportsify.payment.presentation.controller;

import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.CreatePaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestParam Long userId,
            @RequestBody @Valid CreatePaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.createPayment(userId, request));
    }

    @PostMapping("/confirm/mock")
    public ResponseEntity<PaymentResponse> confirmMock(
            @RequestBody @Valid ConfirmPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirmPaymentMock(request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestBody @Valid ConfirmPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }
}