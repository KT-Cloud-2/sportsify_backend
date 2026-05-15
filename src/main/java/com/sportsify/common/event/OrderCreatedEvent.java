package com.sportsify.common.event;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        Long orderId,
        Long memberId,
        Long amount,
        LocalDateTime createdAt
) {
}
