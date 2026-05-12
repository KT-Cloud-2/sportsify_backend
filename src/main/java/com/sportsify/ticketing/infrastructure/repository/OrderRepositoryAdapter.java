package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public void save(Order order) {
        jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return jpaRepository.findById(orderId);
    }

    @Override
    public List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime expiresAt) {
        return jpaRepository.findByStatusAndExpiresAtBefore(status, expiresAt);
    }
}
