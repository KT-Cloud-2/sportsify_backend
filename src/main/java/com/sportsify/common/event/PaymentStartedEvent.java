package com.sportsify.common.event;

import com.sportsify.payment.domain.type.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentStartedEvent(
        Long orderId,
        Long memberId,
        Long paymentId,
        Long amount,
        String paymentKey,
        PaymentStatus paymentStatus,
        LocalDateTime occurredAt
) {
}
