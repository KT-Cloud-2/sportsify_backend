package com.sportsify.payment.application.service;

import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.domain.entity.Payment;
import com.sportsify.payment.domain.exception.InvalidPaymentStatusException;
import com.sportsify.payment.domain.exception.PaymentNotFoundException;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.payment.domain.type.PaymentStatus;
import com.sportsify.payment.infrastructure.toss.TossPaymentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("cancel completed payment")
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
    }

    @Test
    @DisplayName("cancel mock payment without toss cancel call")
    void cancelPayment_mockPayment_success_withoutTossCancel() {
        Long paymentId = 1L;
        Payment payment = completedPayment("MOCK_ORDER_123");
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.cancelPayment(paymentId, request);

        assertThat(response.getStatus()).isEqualTo("CANCELED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelReason()).isEqualTo("cancel request");
        assertThat(payment.getCanceledAt()).isNotNull();

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
    }

    @Test
    @DisplayName("fail when payment not found")
    void cancelPayment_paymentNotFound_fail() {
        Long paymentId = 999L;
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, request))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
    }

    @Test
    @DisplayName("fail when payment is pending")
    void cancelPayment_pendingPayment_fail() {
        Long paymentId = 1L;
        Payment payment = pendingPayment();
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, request))
                .isInstanceOf(InvalidPaymentStatusException.class);

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
    }

    @Test
    @DisplayName("fail when payment already canceled")
    void cancelPayment_alreadyCanceled_fail() {
        Long paymentId = 1L;
        Payment payment = canceledPayment();
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, request))
                .isInstanceOf(InvalidPaymentStatusException.class);

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
    }

    @Test
    @DisplayName("fail when completed payment has no payment key")
    void cancelPayment_emptyPaymentKey_fail() {
        Long paymentId = 1L;
        Payment payment = completedPayment(null);
        CancelPaymentRequest request = cancelRequest("cancel request");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, request))
                .isInstanceOf(InvalidPaymentStatusException.class);

        verify(tossPaymentClient, never()).cancel(anyString(), anyString());
    }

    private Payment pendingPayment() {
        return Payment.builder()
                .userId(1L)
                .matchId(1L)
                .seatId(1L)
                .orderId("ORDER_123")
                .idempotencyKey("IDEMPOTENCY_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status(PaymentStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    private Payment completedPayment(String paymentKey) {
        return Payment.builder()
                .userId(1L)
                .matchId(1L)
                .seatId(1L)
                .orderId("ORDER_123")
                .paymentKey(paymentKey)
                .idempotencyKey("IDEMPOTENCY_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status(PaymentStatus.COMPLETED)
                .requestedAt(LocalDateTime.now())
                .approvedAt(OffsetDateTime.now())
                .build();
    }

    private Payment canceledPayment() {
        return Payment.builder()
                .userId(1L)
                .matchId(1L)
                .seatId(1L)
                .orderId("ORDER_123")
                .paymentKey("PAYMENT_KEY_123")
                .idempotencyKey("IDEMPOTENCY_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status(PaymentStatus.CANCELED)
                .requestedAt(LocalDateTime.now())
                .approvedAt(OffsetDateTime.now())
                .canceledAt(LocalDateTime.now())
                .cancelReason("cancel request")
                .build();
    }

    private CancelPaymentRequest cancelRequest(String cancelReason) {
        CancelPaymentRequest request = new CancelPaymentRequest();
        ReflectionTestUtils.setField(request, "cancelReason", cancelReason);
        return request;
    }
}
