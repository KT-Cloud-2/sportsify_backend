package com.sportsify.ticketing.domain.repository;

import com.sportsify.ticketing.domain.model.Order;

public interface OrderRepository {
    void save(Order createdOrder);
}