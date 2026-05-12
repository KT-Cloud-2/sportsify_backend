package com.sportsify.ticketing.application.listener;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentFailedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderRepository orderRepository;

    @EventListener
    @Transactional
    public void onPaymentStarted(PaymentStartedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.updateStatus(OrderStatus.PAYING);
    }

    @EventListener
    @Transactional
    public void onPaymentSuccess(PaymentCompletedEvent event) {

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.updateStatus(OrderStatus.CONFIRMED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CONFIRMED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.SOLD);
        });
    }

    @EventListener
    @Transactional
    public void onPaymentCancelled(PaymentCancelledEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.updateStatus(OrderStatus.CANCELLED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CANCELLED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.AVAILABLE);
        });
    }

    @EventListener
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.updateExpiresAt(event.failedAt().plusMinutes(15));
    }

}
