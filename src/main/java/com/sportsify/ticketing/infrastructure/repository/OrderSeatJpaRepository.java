package com.sportsify.ticketing.infrastructure.repository;

import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderSeatJpaRepository extends JpaRepository<OrderSeat, Long> {
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OrderSeat os SET os.status = :status WHERE os.order.id IN :orderIds")
    void bulkUpdateOrderSeats(@Param("orderIds") List<Long> orderIds, @Param("status") OrderSeatStatus status);

}
