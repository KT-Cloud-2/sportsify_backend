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
    public Optional<Order> findByIdWithAll(Long id) {
        return jpaRepository.findByIdWithAll(id);
    }

    @Override
    public Optional<Order> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id);
    }

    @Override
    public Long findGameIdByOrderId(Long orderId) {
        return jpaRepository.findGameIdByOrderId(orderId);
    }

    @Override
    public List<Long> findExpiredPendingOrderIdsWithoutPayment(LocalDateTime now) {
        return jpaRepository.findExpiredPendingOrderIdsWithoutPayment(now);
    }

    @Override
    public List<Long> findPendingOrderIdsWithFailedPayment() {
        return jpaRepository.findPendingOrderIdsWithFailedPayment();
    }

    @Override
    public List<Long> findPendingOrderIdsWithCompletedPayment() {
        return jpaRepository.findPendingOrderIdsWithCompletedPayment();
    }

    @Override
    public void bulkUpdateOrders(List<Long> ids, OrderStatus status, LocalDateTime now) {
        jpaRepository.bulkUpdateOrders(ids, status, now);
    }
}
