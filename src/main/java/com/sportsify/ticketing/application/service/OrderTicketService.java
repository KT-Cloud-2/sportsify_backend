package com.sportsify.ticketing.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderTicketService {
    private final OrderRepository orderRepository;
    private final TicketService ticketService;

    @Retryable(
            maxRetries = 1,
            delayString = "500ms",
            excludes = CannotCreateTransactionException.class
    )
    @Transactional(timeout = 2)
    public void completeOrderAndIssueTickets(Long orderId) {
        Order order = getOrder(orderId);
        order.confirm();
        ticketService.createTickets(order);
    }

    @Retryable(
            maxRetries = 1,
            delayString = "500ms",
            excludes = CannotCreateTransactionException.class
    )
    @Transactional(timeout = 2)
    public void cancelOrder(Long orderId) {
        Order order = getOrder(orderId);
        order.cancel();
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findByIdWithAll(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
