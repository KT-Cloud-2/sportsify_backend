package com.sportsify.common.event;

public record PaymentStartedEvent(
        Long orderId
) {
}
