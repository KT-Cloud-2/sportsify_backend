package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderStatus;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order createdOrder);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdWithOrderSeats(Long orderId);

    List<Order> findExpiredPendingOrdersWithoutPayment(LocalDateTime now);

    List<Order> findPayingOrdersWithFailedPayment();

    void bulkUpdateOrders(@Param("ids") List<Long> ids, @Param("status") OrderStatus status, @Param("now") LocalDateTime now);
}
