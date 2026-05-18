package com.sportsify.ticketing.application.listener;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final OrderRepository orderRepository;

    @EventListener
    @Transactional
    public void onPaymentStarted(PaymentStartedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("결제 시작 불가 상태: orderId={}, status={}", event.orderId(), order.getStatus());
            return;
        }

        order.updateStatus(OrderStatus.PAYING);
    }

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSuccess(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PAYING) {
            log.warn("결제 완료 불가 상태: orderId={}, status={}", event.orderId(), order.getStatus());
            return;
        }

        order.updateStatus(OrderStatus.CONFIRMED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CONFIRMED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.SOLD);
        });
    }

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCancelled(PaymentCancelledEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("이미 취소된 주문: orderId={}", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.warn("이미 확정된 주문은 취소 불가: orderId={}", event.orderId());
            return;
        }

        order.updateStatus(OrderStatus.CANCELLED);
        order.updateExpiresAt(null);

        order.getOrderSeats().forEach(orderSeat -> {
            orderSeat.updateStatus(OrderSeatStatus.CANCELLED);
            orderSeat.getGameSeat().updateSeatStatus(SeatStatus.AVAILABLE);
        });
    }
}