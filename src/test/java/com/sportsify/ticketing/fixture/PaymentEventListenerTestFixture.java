package com.sportsify.ticketing.fixture;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentFailedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.payment.domain.type.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PaymentEventListenerTestFixture {

    public PaymentStartedEvent createStartedEventByOrderId(Long orderId) {
        return new PaymentStartedEvent(orderId, 1L, 1L, 1L, "paykey", PaymentStatus.PENDING, LocalDateTime.now());
    }

    public PaymentCompletedEvent createCompletedEventByOrderId(Long orderId) {
        return new PaymentCompletedEvent(orderId, 1L, 1L, 1L, "paykey", PaymentStatus.PENDING, LocalDateTime.now());
    }

    public PaymentCancelledEvent createCancelledEventByOrderId(Long orderId) {
        return new PaymentCancelledEvent(orderId, 1L, 1L, 1L, "paykey", PaymentStatus.PENDING, "test", LocalDateTime.now());
    }

    public PaymentFailedEvent createFailedEventByOrderId(Long orderId, LocalDateTime occurredAt) {
        return new PaymentFailedEvent(orderId, 1L, 1L, 1L, "paykey", PaymentStatus.PENDING, "test", occurredAt);
    }
}
