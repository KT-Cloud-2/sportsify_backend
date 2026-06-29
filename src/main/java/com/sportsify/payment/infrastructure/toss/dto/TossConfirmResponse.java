package com.sportsify.payment.infrastructure.toss.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossConfirmResponse {

    private String paymentKey;
    private String orderId;
    private String status;
    private String method;
    private Long totalAmount;
    private String approvedAt;
}
