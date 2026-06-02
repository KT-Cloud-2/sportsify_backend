package com.sportsify.ticketing.fixture;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.payment.domain.type.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PaymentEventListenerTestFixture {

    public PaymentCompletedEvent createCompletedEventByOrderId(Long orderId, Long memberId) {
        return new PaymentCompletedEvent(orderId, memberId, 1L, 1L, "paykey", PaymentStatus.PENDING, LocalDateTime.now());
    }

    public PaymentCancelledEvent createCancelledEventByOrderId(Long orderId, Long memberId) {
        return new PaymentCancelledEvent(orderId, memberId, 1L, 1L, "paykey", PaymentStatus.PENDING, "test", LocalDateTime.now());
    }
}
