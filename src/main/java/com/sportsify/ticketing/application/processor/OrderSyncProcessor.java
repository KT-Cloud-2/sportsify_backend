package com.sportsify.ticketing.application.processor;

import com.sportsify.ticketing.application.service.OrderPaymentService;
import com.sportsify.ticketing.application.service.TicketService;
import com.sportsify.ticketing.domain.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderSyncProcessor {

    private final OrderPaymentService orderPaymentService;
    private final TicketService ticketService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSingleOrder(Long orderId) {
        Order completedOrder = orderPaymentService.completePayment(orderId);
        ticketService.createTickets(completedOrder);
    }
}
