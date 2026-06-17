package com.sportsify.ticketing.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("[ORDER_COMPLETE] 이미 처리된 주문 스킵: orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("주문 상태: " + order.getStatus());
        }

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
