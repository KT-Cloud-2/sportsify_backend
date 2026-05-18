package com.sportsify.ticketing.infrastructure.repository;


import com.sportsify.ticketing.domain.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderSeatRepositoryAdapter implements OrderSeatRepository {

    private final OrderSeatJpaRepository jpaRepository;
}