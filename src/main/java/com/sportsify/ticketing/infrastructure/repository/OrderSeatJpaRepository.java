package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.OrderSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSeatJpaRepository extends JpaRepository<OrderSeat, Long> {
}