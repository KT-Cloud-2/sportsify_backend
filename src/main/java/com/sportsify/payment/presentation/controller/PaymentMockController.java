package com.sportsify.payment.presentation.controller;

import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile({"local", "dev", "test"})
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentMockController {

    private final PaymentService paymentService;

    @PostMapping("/confirm/mock")
    public ResponseEntity<PaymentResponse> confirmMock(
            @RequestBody @Valid ConfirmPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirmPaymentMock(request));
    }
}
