package com.sportsify.ticketing.application.listener;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.ticketing.application.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderPaymentService orderPaymentService;

    @EventListener
    @Transactional
    public void onPaymentStarted(PaymentStartedEvent event) {
        orderPaymentService.startPayment(event);
    }

    @Retryable(maxRetries = 3, delayString = "1000ms")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSuccess(PaymentCompletedEvent event) {

        orderPaymentService.completePayment(event);
    }

    @Retryable(maxRetries = 3, delayString = "1000ms")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCancelled(PaymentCancelledEvent event) {
        orderPaymentService.cancelPayment(event);
    }
}
