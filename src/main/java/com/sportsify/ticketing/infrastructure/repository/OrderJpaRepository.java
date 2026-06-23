package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.orderSeats os
            JOIN FETCH os.gameSeat
            JOIN FETCH o.member
            WHERE o.id = :orderId
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    Optional<Order> findByIdWithAll(@Param("orderId") Long orderId);

    @Query(value = """
             SELECT o.id FROM orders o
             WHERE o.status = 'PENDING'
             AND o.expires_at < :now
             AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.order_id = o.id)
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true
    )
    List<Long> findExpiredPendingOrderIdsWithoutPayment(@Param("now") LocalDateTime now);

    @Query(value = """
            SELECT o.id FROM orders o
            WHERE o.status = 'PENDING'
            AND EXISTS (
                SELECT 1 FROM payments p
                WHERE p.order_id = o.id
                AND p.status IN ('FAILED', 'CANCELED', 'REFUNDED')
            )
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findPendingOrderIdsWithFailedPayment();


    @Query(value = """
            SELECT o.id FROM orders o
            WHERE o.status = 'PENDING'
            AND EXISTS (
                SELECT 1 FROM payments p
                WHERE p.order_id = o.id
                AND p.status = 'COMPLETED'
            )
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findPendingOrderIdsWithCompletedPayment();


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    @Query("""
                SELECT DISTINCT g.id
                FROM OrderSeat os
                JOIN os.gameSeat gs
                JOIN gs.game g
                WHERE os.order.id = :orderId
            """)
    Long findGameIdByOrderId(@Param("orderId") Long orderId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status, o.updatedAt = :now WHERE o.id IN :ids")
    void bulkUpdateOrders(@Param("ids") List<Long> ids, @Param("status") OrderStatus status, @Param("now") LocalDateTime now);
}
