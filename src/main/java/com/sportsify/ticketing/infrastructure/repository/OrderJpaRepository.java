package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.orderSeats os
            WHERE o.id = :orderId
            """)
    Optional<Order> findByIdWithOrderSeats(@Param("orderId") Long orderId);

    @Query("""
             SELECT DISTINCT o FROM Order o
             JOIN FETCH o.orderSeats os
             JOIN FETCH os.gameSeat
             WHERE o.status = 'PENDING'
             AND o.expiresAt < :now
             AND NOT EXISTS(SELECT p FROM Payment p WHERE p.orderId = o.id)
            """
    )
    List<Order> findExpiredPendingOrdersWithoutPayment(@Param("now") LocalDateTime now);

    @Query("""
             SELECT DISTINCT o FROM Order o
             JOIN FETCH o.orderSeats os
             JOIN FETCH os.gameSeat
             WHERE o.status = 'PAYING'
             AND EXISTS (
                     SELECT p FROM Payment p
                     WHERE p.orderId = o.id
                     AND p.status IN ('FAILED', 'CANCELED', 'REFUNDED')
             )
            """
    )
    List<Order> findPayingOrdersWithFailedPayment();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status, o.updatedAt = :now WHERE o.id IN :ids")
    void bulkUpdateOrders(@Param("ids") List<Long> ids, @Param("status") OrderStatus status, @Param("now") LocalDateTime now);
}
