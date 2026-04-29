package com.sportsify.payment.infrastructure.toss.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossConfirmResponse {

    private String paymentKey;
    private String orderId;
    private Long totalAmount;
    private String method;
    private String approvedAt;
    private String status;
}