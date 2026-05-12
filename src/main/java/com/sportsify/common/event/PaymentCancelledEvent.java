package com.sportsify.common.event;

public record PaymentCancelledEvent(
        Long orderId
) {
}
