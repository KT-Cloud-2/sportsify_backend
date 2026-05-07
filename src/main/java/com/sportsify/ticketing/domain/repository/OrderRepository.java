package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository {
    void save(Order createdOrder);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime expiresAt);
}