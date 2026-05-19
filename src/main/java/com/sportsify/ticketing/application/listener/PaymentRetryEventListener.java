package com.sportsify.ticketing.application.listener;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.event.EventListener;
import org.springframework.resilience.retry.MethodRetryEvent;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryEventListener {

    private static final Set<String> TARGET_METHODS = Set.of(
            "onPaymentSuccess", "onPaymentCancelled"
    );

    @EventListener
    public void onRetryEvent(MethodRetryEvent event) {
        if (!event.isRetryAborted()) return;

        Class<?> declaringClass = event.getMethod().getDeclaringClass();
        if (!PaymentEventListener.class.isAssignableFrom(declaringClass)) return;

        String methodName = event.getMethod().getName();
        if (!TARGET_METHODS.contains(methodName)) return;

        Long orderId = extractOrderId(event);

        log.error("[ORDER_PAYMENT_FAIL_EVENT] method={}, orderId={}, cause={}",
                methodName, orderId, event.getFailure().getMessage(), event.getFailure());
    }

    private Long extractOrderId(MethodRetryEvent event) {
        if (!(event.getSource() instanceof MethodInvocation invocation)) {
            throw new IllegalStateException("MethodRetryEvent의 source가 MethodInvocation이 아닙니다: " + event.getSource().getClass());
        }

        Object[] args = invocation.getArguments();

        if (args.length == 0) return null;

        return switch (args[0]) {
            case PaymentCompletedEvent e -> e.orderId();
            case PaymentCancelledEvent e -> e.orderId();
            default -> null;
        };
    }
}
