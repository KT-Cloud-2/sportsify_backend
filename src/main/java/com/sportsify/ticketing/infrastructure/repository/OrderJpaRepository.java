package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
            JOIN FETCH os.gameSeat
            JOIN FETCH o.member
            WHERE o.id = :orderId
            """)
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

    @Query("""
             SELECT DISTINCT o.id FROM Order o
             WHERE o.status = 'PENDING'
             AND EXISTS (
                     SELECT p FROM Payment p
                     WHERE p.orderId = o.id
                     AND p.status IN ('FAILED', 'CANCELED', 'REFUNDED')
             )
            """
    )
    List<Long> findPendingOrderIdsWithFailedPayment();


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    @Query("""
                SELECT DISTINCT gs.id FROM Order o
                JOIN o.orderSeats os
                JOIN os.gameSeat gs
                WHERE o.id = :orderId
            """)
    Long findGameIdByOrderId(@Param("orderId") Long orderId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status, o.updatedAt = :now WHERE o.id IN :ids")
    void bulkUpdateOrders(@Param("ids") List<Long> ids, @Param("status") OrderStatus status, @Param("now") LocalDateTime now);
}
