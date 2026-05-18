package com.sportsify.common.notification.payload;

public record PaymentCompletedPayload(
        Long paymentId,
        Long memberId,
        int amount
) implements NotificationPayload {}
