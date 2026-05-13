package com.sportsify.ticketing.application.scheduler;

import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatExpirationScheduler {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireReservedSeats() {
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrdersWithSeats(LocalDateTime.now());

        expiredOrders.forEach(order -> {
            order.updateStatus(OrderStatus.EXPIRED);
            List<OrderSeat> orderSeats = order.getOrderSeats();

            orderSeats.forEach(seat -> {
                seat.updateStatus(OrderSeatStatus.EXPIRED);
                seat.getGameSeat().updateSeatStatus(SeatStatus.AVAILABLE);
            });

        });
    }
}
