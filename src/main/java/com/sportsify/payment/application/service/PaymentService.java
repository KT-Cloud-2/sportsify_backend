package com.sportsify.payment.application.service;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.PaymentCompletedPayload;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.repository.GameRepository;
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
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TOSS_PAYMENT_DONE_STATUS = "DONE";

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final OrderRepository orderRepository;
    private final GameRepository gameRepository;

    @Transactional
    public PaymentResponse createPayment(Long userId, CreatePaymentRequest request) {
        validateOrder(request, userId);

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
                            .orderId(request.getOrderId())
                            .tossOrderId(generateTossOrderId(request.getOrderId()))
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
        Payment payment = paymentRepository.findByTossOrderId(request.getTossOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("존재하지 않는 주문입니다."));

        validateConfirmablePayment(payment, request);

        TossConfirmResponse tossResponse = tossPaymentClient.confirm(
                TossConfirmRequest.builder()
                        .paymentKey(request.getPaymentKey())
                        .orderId(request.getTossOrderId())
                        .amount(request.getAmount())
                        .build()
        );

        validateTossConfirmResponse(tossResponse, payment, request);

        payment.markCompleted(
                tossResponse.getPaymentKey(),
                tossResponse.getMethod(),
                parseApprovedAt(tossResponse.getApprovedAt())
        );

        publishPaymentCompletedEvent(payment);
        publishPaymentCompletedNotification(payment);

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse confirmPaymentMock(ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByTossOrderId(request.getTossOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("존재하지 않는 주문입니다."));

        validateConfirmablePayment(payment, request);

        String mockPaymentKey = "MOCK_" + request.getTossOrderId();

        payment.markCompleted(
                mockPaymentKey,
                "CARD",
                OffsetDateTime.now()
        );

        publishPaymentCompletedEvent(payment);
        publishPaymentCompletedNotification(payment);

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
        publishPaymentCancelledEvent(payment, request.getCancelReason());

        return toResponse(payment);
    }

    private void validateConfirmablePayment(Payment payment, ConfirmPaymentRequest request) {
        if (!payment.getAmount().equals(request.getAmount())) {
            throw new InvalidPaymentAmountException("결제 금액이 일치하지 않습니다.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("PENDING 상태의 결제만 승인할 수 있습니다.");
        }
    }

    private void validateTossConfirmResponse(
            TossConfirmResponse tossResponse,
            Payment payment,
            ConfirmPaymentRequest request
    ) {
        if (tossResponse == null) {
            throw new InvalidPaymentStatusException("Toss 결제 승인 응답이 비어 있습니다.");
        }

        if (!Objects.equals(tossResponse.getPaymentKey(), request.getPaymentKey())) {
            throw new InvalidPaymentStatusException("Toss 결제 키가 요청 정보와 일치하지 않습니다.");
        }

        if (!Objects.equals(tossResponse.getOrderId(), request.getTossOrderId())) {
            throw new InvalidPaymentStatusException("Toss 주문 ID가 요청 정보와 일치하지 않습니다.");
        }

        if (!Objects.equals(tossResponse.getTotalAmount(), payment.getAmount())) {
            throw new InvalidPaymentAmountException("Toss 결제 금액이 요청 정보와 일치하지 않습니다.");
        }

        if (!Objects.equals(tossResponse.getStatus(), TOSS_PAYMENT_DONE_STATUS)) {
            throw new InvalidPaymentStatusException("Toss 결제가 완료 상태가 아닙니다.");
        }
    }

    private void validateSamePaymentRequest(
            Payment existingPayment,
            Long userId,
            CreatePaymentRequest request
    ) {
        if (!Objects.equals(existingPayment.getUserId(), userId)
                || !Objects.equals(existingPayment.getOrderId(), request.getOrderId())
                || !Objects.equals(existingPayment.getMatchId(), request.getMatchId())
                || !Objects.equals(existingPayment.getSeatId(), request.getSeatId())
                || !Objects.equals(existingPayment.getAmount(), request.getAmount())
                || !Objects.equals(existingPayment.getPaymentMethod(), request.getPaymentMethod())) {
            throw new InvalidPaymentStatusException("동일한 idempotencyKey로 다른 결제 요청을 생성할 수 없습니다.");
        }
    }

    private OffsetDateTime parseApprovedAt(String approvedAt) {
        if (approvedAt == null || approvedAt.isBlank()) {
            throw new InvalidPaymentStatusException("Toss 결제 승인 시간이 올바르지 않습니다.");
        }

        try {
            return OffsetDateTime.parse(approvedAt);
        } catch (DateTimeParseException e) {
            throw new InvalidPaymentStatusException("Toss 결제 승인 시간이 올바르지 않습니다.");
        }
    }

    private String generateTossOrderId(Long orderId) {
        return "ORDER_" + orderId + "_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);
    }

    private void publishPaymentCompletedEvent(Payment payment) {
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getStatus(),
                LocalDateTime.now()
        ));
    }

    private void publishPaymentCompletedNotification(Payment payment) {
        notificationEventPublisher.publish(
                NotificationEventType.PAYMENT_COMPLETED,
                new PaymentCompletedPayload(
                        payment.getId(),
                        payment.getUserId(),
                        Math.toIntExact(payment.getAmount())
                )
        );

        log.info("결제 완료 알림 발행 완료. paymentId={}, userId={}",
                payment.getId(),
                payment.getUserId());
    }

    private void publishPaymentCancelledEvent(Payment payment, String cancelReason) {
        eventPublisher.publishEvent(new PaymentCancelledEvent(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getStatus(),
                cancelReason,
                LocalDateTime.now()
        ));
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .tossOrderId(payment.getTossOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus().name())
                .requestedAt(payment.getRequestedAt())
                .approvedAt(payment.getApprovedAt())
                .build();
    }

    private void validateOrder(CreatePaymentRequest request, Long userId) {
        Order order = orderRepository.findByIdWithLock(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!(order.getMemberId().equals(userId))) {
            throw new BusinessException(ErrorCode.ORDER_MEMBER_MISMATCH);
        }

        if (order.isClosed()) {
            log.warn("결제 시작 불가 상태: orderId={}, status={}", request.getOrderId(), order.getStatus());
            throw new BusinessException(ErrorCode.ORDER_CLOSED, "status: " + order.getStatus());
        }

        if (!(order.getTotalAmount().equals(request.getAmount()))) {
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH);
        }

        Long gameId = orderRepository.findGameIdByOrderId(order.getId());
        if (!gameId.equals(request.getMatchId())) {
            throw new BusinessException(ErrorCode.GAME_MISMATCH);
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        if (!game.isOnSale()) {
            throw new BusinessException(ErrorCode.GAME_NOT_ON_SALE);
        }
    }
}
