package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.model.Order;
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
    public List<Order> findExpiredPendingOrdersWithoutPayment(LocalDateTime now) {
        return jpaRepository.findExpiredPendingOrdersWithoutPayment(now);
    }

    @Override
    public List<Order> findPayingOrdersWithFailedPayment() {
        return jpaRepository.findPayingOrdersWithFailedPayment();
    }

    ;

}
