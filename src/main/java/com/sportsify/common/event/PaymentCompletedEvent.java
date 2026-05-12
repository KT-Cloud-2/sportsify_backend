package com.sportsify.common.event;

public record PaymentCompletedEvent(
        Long orderId
) {

}
