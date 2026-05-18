package com.sportsify.common.event;

import com.sportsify.payment.domain.type.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentCancelledEvent(
        Long orderId,
        Long memberId,
        Long paymentId,
        Long amount,
        String paymentKey,
        PaymentStatus paymentStatus,
        String failureReason,
        LocalDateTime occurredAt
) {
}
