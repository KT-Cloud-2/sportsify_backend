package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order createdOrder);

    Optional<Order> findById(Long id);

    List<Order> findExpiredPendingOrdersWithoutPayment(LocalDateTime now);

    List<Order> findPayingOrdersWithFailedPayment();
}
