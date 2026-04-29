package com.sportsify.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    private Long paymentId;
    private String orderId;
    private String paymentKey;
    private Long amount;
    private String paymentMethod;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
}