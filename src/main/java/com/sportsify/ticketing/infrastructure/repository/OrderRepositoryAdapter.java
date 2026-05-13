package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public void save(Order order) {
        jpaRepository.save(order);
    }

    @Override
    public List<Order> findExpiredPendingOrdersWithSeats(LocalDateTime now) {
        return jpaRepository.findExpiredPendingOrdersWithSeats(now);
    }
}
