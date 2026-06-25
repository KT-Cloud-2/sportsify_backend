package com.sportsify.ticketing.application.listener;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.ticketing.application.service.OrderPaymentService;
import com.sportsify.ticketing.application.service.TicketService;
import com.sportsify.ticketing.domain.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderPaymentService orderPaymentService;
    private final TicketService ticketService;

    @Retryable(
            maxRetries = 1,
            delayString = "500ms",
            excludes = CannotCreateTransactionException.class
    )
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void onPaymentSuccess(PaymentCompletedEvent event) {

        Order completedOrder = orderPaymentService.completePayment(event);
        ticketService.createTickets(completedOrder);
    }

    @Retryable(
            maxRetries = 1,
            delayString = "500ms",
            excludes = CannotCreateTransactionException.class
    )
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void onPaymentCancelled(PaymentCancelledEvent event) {
        orderPaymentService.cancelPayment(event);
    }
}
