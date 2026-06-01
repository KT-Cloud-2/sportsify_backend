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
    public Order save(Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithOrderSeats(Long orderId) {
        return jpaRepository.findByIdWithOrderSeats(orderId);
    }

    @Override
    public List<Long> findExpiredPendingOrderIdsWithoutPayment(LocalDateTime now) {
        return jpaRepository.findExpiredPendingOrderIdsWithoutPayment(now);
    }

    @Override
    public List<Long> findPayingOrderIdsWithFailedPayment() {
        return jpaRepository.findPayingOrderIdsWithFailedPayment();
    }

    @Override
    public void bulkUpdateOrders(List<Long> ids, OrderStatus status, LocalDateTime now) {
        jpaRepository.bulkUpdateOrders(ids, status, now);
    }
}
