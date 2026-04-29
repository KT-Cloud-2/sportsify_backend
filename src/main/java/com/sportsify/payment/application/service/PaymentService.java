package com.sportsify.payment.application.service;

import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.CreatePaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.domain.entity.Payment;
import com.sportsify.payment.domain.exception.InvalidPaymentAmountException;
import com.sportsify.payment.domain.exception.InvalidPaymentStatusException;
import com.sportsify.payment.domain.exception.PaymentNotFoundException;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.payment.domain.type.PaymentStatus;
import com.sportsify.payment.infrastructure.toss.TossPaymentClient;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    @Transactional
    public PaymentResponse createPayment(Long userId, CreatePaymentRequest request) {
        String orderId = generateOrderId();
        String idempotencyKey = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .userId(userId)
                .matchId(request.getMatchId())
                .seatId(request.getSeatId())
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResponse.builder()
                .paymentId(savedPayment.getId())
                .orderId(savedPayment.getOrderId())
                .amount(savedPayment.getAmount())
                .paymentMethod(savedPayment.getPaymentMethod())
                .status(savedPayment.getStatus().name())
                .requestedAt(savedPayment.getRequestedAt())
                .build();
    }

    @Transactional
    public PaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("존재하지 않는 주문입니다."));

        if (!payment.getAmount().equals(request.getAmount())) {
            throw new InvalidPaymentAmountException("결제 금액이 일치하지 않습니다.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("PENDING 상태의 결제만 승인할 수 있습니다.");
        }

        TossConfirmResponse tossResponse = tossPaymentClient.confirm(
                TossConfirmRequest.builder()
                        .paymentKey(request.getPaymentKey())
                        .orderId(request.getOrderId())
                        .amount(request.getAmount())
                        .build()
        );

        payment.markCompleted(
                tossResponse.getPaymentKey(),
                tossResponse.getMethod(),
                parseApprovedAt(tossResponse.getApprovedAt())
        );

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus().name())
                .requestedAt(payment.getRequestedAt())
                .approvedAt(payment.getApprovedAt())
                .build();
    }

    @Transactional
    public PaymentResponse confirmPaymentMock(ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("존재하지 않는 주문입니다."));

        if (!payment.getAmount().equals(request.getAmount())) {
            throw new InvalidPaymentAmountException("결제 금액이 일치하지 않습니다.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("PENDING 상태의 결제만 승인할 수 있습니다.");
        }

        String mockPaymentKey = "MOCK_" + request.getOrderId();

        payment.markCompleted(
                mockPaymentKey,
                "CARD",
                LocalDateTime.now()
        );

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus().name())
                .requestedAt(payment.getRequestedAt())
                .approvedAt(payment.getApprovedAt())
                .build();
    }

    private String generateOrderId() {
        return "ORDER_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);
    }

    private LocalDateTime parseApprovedAt(String approvedAt) {
        if (approvedAt == null || approvedAt.isBlank()) {
            return LocalDateTime.now();
        }

        return OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }
}