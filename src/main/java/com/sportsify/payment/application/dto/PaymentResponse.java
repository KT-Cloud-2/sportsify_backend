package com.sportsify.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private String tossOrderId;
    private String paymentKey;
    private Long amount;
    private String paymentMethod;
    private String status;
    private LocalDateTime requestedAt;
    private OffsetDateTime approvedAt;
}
