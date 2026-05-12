package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    void save(Order createdOrder);

    Optional<Order> findById(Long orderId);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime expiresAt);
}
