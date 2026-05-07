package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime expiresAt);
}