package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderSeatRepositoryAdapter implements OrderSeatRepository {

    private final OrderSeatJpaRepository jpaRepository;

    @Override
    public void bulkUpdateOrderSeats(@Param("orderIds") List<Long> orderIds, @Param("status") OrderSeatStatus status) {
        jpaRepository.bulkUpdateOrderSeats(orderIds, status);
    }
}
