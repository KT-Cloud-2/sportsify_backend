package com.sportsify.ticketing.application;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.ticketing.application.listener.PaymentEventListener;
import com.sportsify.ticketing.application.listener.PaymentRetryEventListener;
import com.sportsify.ticketing.fixture.PaymentEventListenerTestFixture;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.resilience.retry.MethodRetryEvent;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class PaymentRetryEventListenerTest {

    @InjectMocks
    private PaymentRetryEventListener paymentRetryEventListener;

    @Test
    @DisplayName("아직 재시도 중이면 아무것도 하지 않고 리턴한다.")
    void eventIsNotRetryAborted() {

        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        when(mockEvent.isRetryAborted()).thenReturn(false);

        paymentRetryEventListener.onRetryEvent(mockEvent);

        verify(mockEvent, never()).getMethod();
        verify(mockEvent, never()).getSource();
    }

    @Test
    @DisplayName("PaymentEventListener 클래스가 아니면 리턴한다.")
    void eventIsNotPaymentEventListener() throws NoSuchMethodException {

        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);

        Method irrelevantMethod = String.class.getMethod("toString");

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(irrelevantMethod);

        paymentRetryEventListener.onRetryEvent(mockEvent);

        verify(mockEvent, never()).getSource();
        verify(mockEvent, never()).getFailure();
    }

    @Test
    @DisplayName("onPaymentSuccess나 onPaymentCancelled가 아니면 리턴한다.")
    void eventIsNotTarget() throws NoSuchMethodException {
        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        Method targetMethod = PaymentEventListener.class.getMethod("onPaymentStarted", PaymentStartedEvent.class);

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(targetMethod);

        paymentRetryEventListener.onRetryEvent(mockEvent);

        verify(mockEvent, never()).getSource();
        verify(mockEvent, never()).getFailure();
    }

    @Test
    @DisplayName("이벤트가 파라미터로 넘어오지 않았다면 null을 반환한다.")
    void returnNull_noEventParameter(CapturedOutput output) throws NoSuchMethodException {
        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        Method targetMethod = PaymentEventListener.class.getMethod("onPaymentSuccess", PaymentCompletedEvent.class);
        MethodInvocation mockInvocation = mock(MethodInvocation.class);

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(targetMethod);
        when(mockEvent.getSource()).thenReturn(mockInvocation);
        when(mockEvent.getFailure()).thenReturn(new RuntimeException());
        when(mockInvocation.getArguments()).thenReturn(new Object[]{});

        paymentRetryEventListener.onRetryEvent(mockEvent);

        assertThat(output.getOut()).contains("[ORDER_PAYMENT_FAIL_EVENT]")
                .contains("orderId=null");
    }

    @Test
    @DisplayName("PaymentCompletedEvent가 재시도되면, 재시도 로그가 쌓인다.")
    void log_paymentCompletedEvent(CapturedOutput output) throws NoSuchMethodException {
        PaymentEventListenerTestFixture fixture = new PaymentEventListenerTestFixture();

        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        Method targetMethod = PaymentEventListener.class.getMethod("onPaymentSuccess", PaymentCompletedEvent.class);
        MethodInvocation mockInvocation = mock(MethodInvocation.class);

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(targetMethod);
        when(mockEvent.getSource()).thenReturn(mockInvocation);
        when(mockEvent.getFailure()).thenReturn(new RuntimeException());
        when(mockInvocation.getArguments()).thenReturn(new Object[]{fixture.createCompletedEventByOrderId(1L, 1L)});

        paymentRetryEventListener.onRetryEvent(mockEvent);

        assertThat(output.getOut()).contains("[ORDER_PAYMENT_FAIL_EVENT]")
                .contains("method=onPaymentSuccess")
                .contains("orderId=1");
    }

    @Test
    @DisplayName("PaymentCancelledEvent가 재시도되면, 재시도 로그가 쌓인다.")
    void log_paymentCancelledEvent(CapturedOutput output) throws NoSuchMethodException {
        PaymentEventListenerTestFixture fixture = new PaymentEventListenerTestFixture();

        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        Method targetMethod = PaymentEventListener.class.getMethod("onPaymentCancelled", PaymentCancelledEvent.class);
        MethodInvocation mockInvocation = mock(MethodInvocation.class);

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(targetMethod);
        when(mockEvent.getSource()).thenReturn(mockInvocation);
        when(mockEvent.getFailure()).thenReturn(new RuntimeException());
        when(mockInvocation.getArguments()).thenReturn(new Object[]{fixture.createCancelledEventByOrderId(1L, 1L)});

        paymentRetryEventListener.onRetryEvent(mockEvent);

        assertThat(output.getOut()).contains("[ORDER_PAYMENT_FAIL_EVENT]")
                .contains("method=onPaymentCancelled")
                .contains("orderId=1");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입이면 예외처리된다.")
    void returnNull_unknownEventType() throws NoSuchMethodException {
        MethodRetryEvent mockEvent = mock(MethodRetryEvent.class);
        Method targetMethod = PaymentEventListener.class.getMethod("onPaymentSuccess", PaymentCompletedEvent.class);
        MethodInvocation mockInvocation = mock(MethodInvocation.class);

        when(mockEvent.isRetryAborted()).thenReturn(true);
        when(mockEvent.getMethod()).thenReturn(targetMethod);
        when(mockEvent.getSource()).thenReturn(mockInvocation);
        when(mockInvocation.getArguments()).thenReturn(new Object[]{"알 수 없는 타입"});

        assertThatThrownBy(() -> paymentRetryEventListener.onRetryEvent(mockEvent))
                .isInstanceOf(IllegalStateException.class)
                .extracting(e -> e.getMessage().contains("지원하지 않는 이벤트 타입"));

    }
}
