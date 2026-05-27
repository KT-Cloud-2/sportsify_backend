package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderSeatRepository {
    void bulkUpdateOrderSeats(@Param("orderIds") List<Long> orderIds, @Param("status") OrderSeatStatus status);
}
