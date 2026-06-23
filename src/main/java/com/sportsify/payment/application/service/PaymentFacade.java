package com.sportsify.payment.application.service;

import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.ticketing.application.service.OrderTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderTicketService orderTicketService;

    public PaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        PaymentResponse response = paymentService.confirmPaymentMock(request);

        try {
            orderTicketService.completeOrderAndIssueTickets(response.getOrderId());
        } catch (PessimisticLockingFailureException e) {
            log.info("[PAYMENT_CONFIRM] orderId={} 스케줄러가 처리 중, 위임", response.getOrderId());
        } catch (Exception e) {
            log.error("[ORDER_TICKET_FAIL] orderId={}", response.getOrderId(), e);
        }

        return response;
    }

    public PaymentResponse cancelPayment(Long paymentId, CancelPaymentRequest request) {
        PaymentResponse response = paymentService.cancelPayment(paymentId, request);

        try {
            orderTicketService.cancelOrder(response.getOrderId());
        } catch (Exception e) {
            log.error("[ORDER_CANCEL_FAIL] orderId={}", response.getOrderId(), e);
        }

        return response;
    }
}
