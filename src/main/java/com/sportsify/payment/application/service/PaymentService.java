package com.sportsify.payment.application.service;

import com.sportsify.payment.application.dto.CancelPaymentRequest;
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
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    @Transactional
    public PaymentResponse createPayment(Long userId, CreatePaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .map(existingPayment -> {
                    validateSamePaymentRequest(existingPayment, userId, request);
                    return toResponse(existingPayment);
                })
                .orElseGet(() -> {
                    Payment payment = Payment.builder()
                            .userId(userId)
                            .matchId(request.getMatchId())
                            .seatId(request.getSeatId())
                            .orderId(generateOrderId())
                            .idempotencyKey(request.getIdempotencyKey())
                            .amount(request.getAmount())
                            .paymentMethod(request.getPaymentMethod())
                            .status(PaymentStatus.PENDING)
                            .requestedAt(LocalDateTime.now())
                            .build();

                    Payment savedPayment = paymentRepository.save(payment);

                    return toResponse(savedPayment);
                });
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

        return toResponse(payment);
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

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(Long paymentId, CancelPaymentRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("존재하지 않는 결제입니다."));

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new InvalidPaymentStatusException("이미 취소된 결제입니다.");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStatusException("완료된 결제만 취소할 수 있습니다.");
        }

        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new InvalidPaymentStatusException("결제 키가 없는 결제는 취소할 수 없습니다.");
        }

        if (!payment.getPaymentKey().startsWith("MOCK_")) {
            tossPaymentClient.cancel(payment.getPaymentKey(), request.getCancelReason());
        }

        payment.markCanceled(request.getCancelReason(), LocalDateTime.now());

        return toResponse(payment);
    }

    private void validateSamePaymentRequest(
            Payment payment,
            Long userId,
            CreatePaymentRequest request
    ) {
        boolean sameRequest =
                Objects.equals(payment.getUserId(), userId)
                        && Objects.equals(payment.getMatchId(), request.getMatchId())
                        && Objects.equals(payment.getSeatId(), request.getSeatId())
                        && Objects.equals(payment.getAmount(), request.getAmount())
                        && Objects.equals(payment.getPaymentMethod(), request.getPaymentMethod());

        if (!sameRequest) {
            throw new InvalidPaymentStatusException("동일한 idempotencyKey로 다른 결제 요청을 생성할 수 없습니다.");
        }
    }

    private PaymentResponse toResponse(Payment payment) {
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
