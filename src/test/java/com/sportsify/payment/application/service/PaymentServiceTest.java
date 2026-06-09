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
import com.sportsify.payment.domain.exception.PaymentNotFoundException;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.payment.domain.type.PaymentStatus;
import com.sportsify.payment.infrastructure.toss.TossPaymentClient;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("주문이 존재하지 않으면 결제 생성에 실패한다")
    void createPayment_orderNotFound() {
        CreatePaymentRequest request = createPaymentRequest();

        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 소유자와 요청 사용자가 다르면 결제 생성에 실패한다")
    void createPayment_memberMismatch() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);

        when(mockOrder.getMemberId()).thenReturn(2L);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_MEMBER_MISMATCH);
    }

    @Test
    @DisplayName("주문이 종료 상태이면 결제 생성에 실패한다")
    void createPayment_orderClosed() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);

        when(mockOrder.getMemberId()).thenReturn(1L);
        when(mockOrder.isClosed()).thenReturn(true);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CLOSED);
    }

    @Test
    @DisplayName("주문 금액과 결제 금액이 다르면 결제 생성에 실패한다")
    void createPayment_amountMismatch() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);

        when(mockOrder.getMemberId()).thenReturn(1L);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(10000L);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("주문의 경기 정보와 요청 경기 정보가 다르면 결제 생성에 실패한다")
    void createPayment_gameMismatch() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);

        when(mockOrder.getMemberId()).thenReturn(1L);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(-1L);

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_MISMATCH);
    }

    @Test
    @DisplayName("경기 정보가 존재하지 않으면 결제 생성에 실패한다")
    void createPayment_gameNotFound() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);

        when(mockOrder.getMemberId()).thenReturn(1L);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(1L);
        when(gameRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_NOT_FOUND);
    }

    @Test
    @DisplayName("경기가 판매중이 아니면 결제 생성에 실패한다")
    void createPayment_gameNotOnSale() {
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);
        Game mockGame = mock(Game.class);

        when(mockOrder.getMemberId()).thenReturn(1L);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(mockGame.isOnSale()).thenReturn(false);
        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(1L);
        when(gameRepository.findById(any())).thenReturn(Optional.of(mockGame));

        assertThatThrownBy(() -> paymentService.createPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GAME_NOT_ON_SALE);
    }

    @Test
    @DisplayName("결제를 생성한다")
    void createPayment_success_publishPaymentStartedEvent() {
        Long userId = 1L;
        CreatePaymentRequest request = createPaymentRequest();
        Order mockOrder = mock(Order.class);
        Game mockGame = mock(Game.class);

        when(mockOrder.getId()).thenReturn(1L);
        when(mockOrder.getMemberId()).thenReturn(userId);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(mockGame.isOnSale()).thenReturn(true);

        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(1L);
        when(gameRepository.findById(any())).thenReturn(Optional.of(mockGame));

        when(paymentRepository.findByIdempotencyKey("IDEMPOTENCY_KEY_123"))
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(payment, "id", 1L);
                    return payment;
                });

        PaymentResponse response = paymentService.createPayment(userId, request);

        assertThat(response.getPaymentId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getTossOrderId()).startsWith("ORDER_1_");
        assertThat(response.getAmount()).isEqualTo(50000L);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verifyNoInteractions(notificationEventPublisher);
    }

    @Test
    @DisplayName("동일한 멱등성 키로 완전히 같은 요청이 오면 기존 결제 정보를 그대로 반환한다")
    void createPayment_idempotency_success() {
        Long userId = 1L;
        CreatePaymentRequest request = createPaymentRequest();
        Payment existingPayment = pendingPayment();

        Order mockOrder = mock(Order.class);
        Game mockGame = mock(Game.class);

        when(mockOrder.getId()).thenReturn(1L);
        when(mockOrder.getMemberId()).thenReturn(userId);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(mockGame.isOnSale()).thenReturn(true);

        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(1L);
        when(gameRepository.findById(any())).thenReturn(Optional.of(mockGame));

        when(paymentRepository.findByIdempotencyKey("IDEMPOTENCY_KEY_123"))
                .thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.createPayment(userId, request);

        assertThat(response.getPaymentId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getAmount()).isEqualTo(50000L);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("동일한 멱등성 키이지만 내부 요청 데이터가 상이하면 결제 생성에 실패한다")
    void createPayment_idempotency_failDueToDataMismatch() {
        Long userId = 1L;
        CreatePaymentRequest request = createPaymentRequest();
        Payment existingPayment = pendingPayment();

        Order mockOrder = mock(Order.class);
        Game mockGame = mock(Game.class);

        when(mockOrder.getId()).thenReturn(1L);
        when(mockOrder.getMemberId()).thenReturn(userId);
        when(mockOrder.isClosed()).thenReturn(false);
        when(mockOrder.getTotalAmount()).thenReturn(50000L);
        when(mockGame.isOnSale()).thenReturn(true);

        when(orderRepository.findByIdWithLock(any())).thenReturn(Optional.of(mockOrder));
        when(orderRepository.findGameIdByOrderId(any())).thenReturn(1L);
        when(gameRepository.findById(any())).thenReturn(Optional.of(mockGame));

        ReflectionTestUtils.setField(existingPayment, "userId", 999L);

        when(paymentRepository.findByIdempotencyKey("IDEMPOTENCY_KEY_123"))
                .thenReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentService.createPayment(userId, request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("모의 결제 승인 시 결제 완료 이벤트와 알림을 발행한다")
    void confirmPaymentMock_success_publishPaymentCompletedEvent() {
        ConfirmPaymentRequest request = confirmPaymentRequest();
        Payment payment = pendingPayment();

        when(paymentRepository.findByTossOrderId("ORDER_1_TEST"))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.confirmPaymentMock(request);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaymentKey()).isEqualTo("MOCK_ORDER_1_TEST");
        assertThat(payment.getApprovedAt()).isNotNull();

        ArgumentCaptor<PaymentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        PaymentCompletedEvent event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.memberId()).isEqualTo(1L);
        assertThat(event.paymentId()).isEqualTo(1L);
        assertThat(event.amount()).isEqualTo(50000L);
        assertThat(event.paymentKey()).isEqualTo("MOCK_ORDER_1_TEST");
        assertThat(event.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);

        ArgumentCaptor<PaymentCompletedPayload> payloadCaptor =
                ArgumentCaptor.forClass(PaymentCompletedPayload.class);

        verify(notificationEventPublisher).publish(
                eq(NotificationEventType.PAYMENT_COMPLETED),
                payloadCaptor.capture()
        );

        PaymentCompletedPayload payload = payloadCaptor.getValue();
        assertThat(payload.paymentId()).isEqualTo(1L);
        assertThat(payload.memberId()).isEqualTo(1L);
        assertThat(payload.amount()).isEqualTo(50000);
    }

    @Test
    @DisplayName("완료된 결제를 취소한다")
    void cancelPayment_success() {
        Long paymentId = 1L;
        Payment payment = completedPayment("PAYMENT_KEY_123");
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.cancelPayment(paymentId, request);

        assertThat(response.getStatus()).isEqualTo("CANCELED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelReason()).isEqualTo("cancel request");
        assertThat(payment.getCanceledAt()).isNotNull();

        verify(tossPaymentClient).cancel("PAYMENT_KEY_123", "cancel request");

        ArgumentCaptor<PaymentCancelledEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentCancelledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        PaymentCancelledEvent event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.memberId()).isEqualTo(1L);
        assertThat(event.paymentId()).isEqualTo(1L);
        assertThat(event.amount()).isEqualTo(50000L);
        assertThat(event.paymentKey()).isEqualTo("PAYMENT_KEY_123");
        assertThat(event.paymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(event.failureReason()).isEqualTo("cancel request");

        verifyNoInteractions(notificationEventPublisher);
    }

    @Test
    @DisplayName("모의 결제는 Toss 취소 호출 없이 취소한다")
    void cancelPayment_mockPayment_success_withoutTossCancel() {
        Long paymentId = 1L;
        Payment payment = completedPayment("MOCK_ORDER_123");
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.cancelPayment(paymentId, request);

        assertThat(response.getStatus()).isEqualTo("CANCELED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
        verify(eventPublisher).publishEvent(any(PaymentCancelledEvent.class));
        verifyNoInteractions(notificationEventPublisher);
    }

    @Test
    @DisplayName("결제가 존재하지 않으면 취소에 실패한다")
    void cancelPayment_paymentNotFound_fail() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.cancelPayment(999L, cancelRequest("cancel request")))
                .isInstanceOf(PaymentNotFoundException.class);

        verifyNoInteractions(notificationEventPublisher);
    }

    private Payment pendingPayment() {
        Payment payment = Payment.builder()
                .userId(1L)
                .matchId(1L)
                .seatId(1L)
                .orderId(1L)
                .tossOrderId("ORDER_1_TEST")
                .idempotencyKey("IDEMPOTENCY_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status(PaymentStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    private Payment completedPayment(String paymentKey) {
        Payment payment = Payment.builder()
                .userId(1L)
                .matchId(1L)
                .seatId(1L)
                .orderId(1L)
                .tossOrderId("ORDER_1_TEST")
                .paymentKey(paymentKey)
                .idempotencyKey("IDEMPOTENCY_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status(PaymentStatus.COMPLETED)
                .requestedAt(LocalDateTime.now())
                .approvedAt(OffsetDateTime.now())
                .build();

        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    private CreatePaymentRequest createPaymentRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        ReflectionTestUtils.setField(request, "orderId", 1L);
        ReflectionTestUtils.setField(request, "matchId", 1L);
        ReflectionTestUtils.setField(request, "seatId", 1L);
        ReflectionTestUtils.setField(request, "amount", 50000L);
        ReflectionTestUtils.setField(request, "paymentMethod", "CARD");
        ReflectionTestUtils.setField(request, "idempotencyKey", "IDEMPOTENCY_KEY_123");
        return request;
    }

    private ConfirmPaymentRequest confirmPaymentRequest() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        ReflectionTestUtils.setField(request, "paymentKey", "PAYMENT_KEY_123");
        ReflectionTestUtils.setField(request, "tossOrderId", "ORDER_1_TEST");
        ReflectionTestUtils.setField(request, "amount", 50000L);
        return request;
    }

    private CancelPaymentRequest cancelRequest(String cancelReason) {
        CancelPaymentRequest request = new CancelPaymentRequest();
        ReflectionTestUtils.setField(request, "cancelReason", cancelReason);
        return request;
    }
}
