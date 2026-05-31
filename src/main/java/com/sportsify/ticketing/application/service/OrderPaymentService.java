package com.sportsify.ticketing.application.service;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentService {

    private final OrderRepository orderRepository;

    public Order completePayment(PaymentCompletedEvent event) {

        Order order = orderRepository.findByIdWithAll(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "결제 완료 불가 상태: orderId=" + event.orderId() + ", status=" + order.getStatus()
            );
        }

        order.updateStatus(OrderStatus.CONFIRMED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CONFIRMED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.SOLD);
        });

        return order;
    }

    public void cancelPayment(PaymentCancelledEvent event) {
        Order order = orderRepository.findByIdWithAll(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "결제 취소 불가 상태: orderId=" + event.orderId() + ", status=" + order.getStatus()
            );
        }

        order.updateStatus(OrderStatus.CANCELLED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CANCELLED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.AVAILABLE);
        });
    }
}
