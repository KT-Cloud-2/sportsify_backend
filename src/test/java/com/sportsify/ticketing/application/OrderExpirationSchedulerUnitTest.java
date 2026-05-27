package com.sportsify.ticketing.application;

import com.sportsify.ticketing.application.scheduler.OrderExpirationScheduler;
import com.sportsify.ticketing.application.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class OrderExpirationSchedulerUnitTest {

    @InjectMocks
    private OrderExpirationScheduler scheduler;

    @Mock
    private OrderService orderService;

    @Test
    @DisplayName("미결제 만료 처리에서 오류가 발생하면 예외를 삼키고 로그만 남긴다.")
    void expireUnpaidOrders_catchesException(CapturedOutput output) {

        doThrow(new RuntimeException("DB 에러"))
                .when(orderService).expireUnpaidOrdersBulk();

        assertThatCode(() -> scheduler.releaseUnpaidOrders())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 미결제 만료 처리 실패");
        verify(orderService).cancelFailedPaymentOrdersBulk();

    }

    @Test
    @DisplayName("결제 실패 취소 처리에서 오류가 발생해도 예외를 삼킨다")
    void cancelFailedPayment_catchesException(CapturedOutput output) {
        doThrow(new RuntimeException("DB 에러"))
                .when(orderService).cancelFailedPaymentOrdersBulk();

        assertThatCode(() -> scheduler.releaseUnpaidOrders())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제 실패 건 취소 처리 실패");
        verify(orderService).expireUnpaidOrdersBulk();
    }

    @Test
    @DisplayName("둘 다 실패해도 예외를 삼킨다")
    void bothFail_catchesException(CapturedOutput output) {
        doThrow(new RuntimeException("expire 에러"))
                .when(orderService).expireUnpaidOrdersBulk();
        doThrow(new RuntimeException("cancel 에러"))
                .when(orderService).cancelFailedPaymentOrdersBulk();

        assertThatCode(() -> scheduler.releaseUnpaidOrders())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 미결제 만료 처리 실패");
        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제 실패 건 취소 처리 실패");
    }
}
