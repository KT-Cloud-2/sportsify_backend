package com.sportsify.common.event;

import java.time.LocalDateTime;

public record PaymentFailedEvent(
        Long orderId,
        LocalDateTime failedAt
) {
}
