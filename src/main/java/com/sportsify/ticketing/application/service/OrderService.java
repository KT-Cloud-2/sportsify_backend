package com.sportsify.ticketing.application.service;

import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;

    private final OrderSeatRepository orderSeatRepository;

    private final GameSeatRepository gameSeatRepository;

    @Transactional
    public void expireUnpaidOrdersBulk() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = orderRepository.findExpiredPendingOrdersWithoutPayment(now);
        if (orders.isEmpty()) return;

        log.info("[ORDER_SCHEDULER] Unpaid Bulk size: {}", orders.size());

        releaseSeatsBulk(orders, now, OrderSeatStatus.EXPIRED, OrderStatus.EXPIRED);
    }

    @Transactional
    public void cancelFailedPaymentOrdersBulk() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = orderRepository.findPayingOrdersWithFailedPayment();

        if (orders.isEmpty()) return;

        log.info("[ORDER_SCHEDULER] Failed Bulk size: {}", orders.size());

        releaseSeatsBulk(orders, now, OrderSeatStatus.CANCELLED, OrderStatus.CANCELLED);
    }

    public void releaseSeatsBulk(List<Order> orders, LocalDateTime now, OrderSeatStatus orderSeatstatus, OrderStatus orderStatus) {
        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        gameSeatRepository.bulkReleaseGameSeatsByOrderIds(orderIds);
        orderSeatRepository.bulkUpdateOrderSeats(orderIds, orderSeatstatus);
        orderRepository.bulkUpdateOrders(orderIds, orderStatus, now);
    }
}
