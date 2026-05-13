package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("SELECT DISTINCT o FROM Order o " +
            "JOIN FETCH o.orderSeats os " +
            "JOIN FETCH os.gameSeat " +
            "WHERE o.status = 'PENDING' AND o.expiresAt < :now"
    )
    List<Order> findExpiredPendingOrdersWithSeats(@Param("now") LocalDateTime now);
}
