package com.sportsify.ticketing.application;

import com.sportsify.ticketing.application.scheduler.OrderMaintenanceScheduler;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class OrderMaintenanceSchedulerUnitTest {

    @InjectMocks
    private OrderMaintenanceScheduler scheduler;

    @Mock
    private OrderService orderService;


    @Test
    @DisplayName("판매 중인 게임이 없으면 스케줄러가 DB 조회를 하지 않는다")
    void schedulerSkipsWhenNoActiveSale() {
        scheduler.onSaleStarted();
        scheduler.onSaleEnded();
        scheduler.processOrderMaintenance();

        verify(orderService, never()).expireUnpaidOrdersBulk();
        verify(orderService, never()).cancelFailedPaymentOrdersBulk();
        verify(orderService, never()).completeStuckOrders();
    }

    @Test
    @DisplayName("판매 중일 때 스케줄러가 DB 조회를 실행한다")
    void schedulerRunsWhenSaleActive() {
        scheduler.onSaleStarted();

        scheduler.processOrderMaintenance();

        verify(orderService).expireUnpaidOrdersBulk();
        verify(orderService).cancelFailedPaymentOrdersBulk();
        verify(orderService).completeStuckOrders();
    }

    @Test
    @DisplayName("미결제 만료 처리에서 오류가 발생하면 로그를 남기고 예외를 삼킨다")
    void expireUnpaidOrders_catchesException(CapturedOutput output) {
        scheduler.onSaleStarted();

        doThrow(new RuntimeException("DB 에러"))
                .when(orderService).expireUnpaidOrdersBulk();

        assertThatCode(() -> scheduler.processOrderMaintenance())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 미결제 만료 처리 실패");
        verify(orderService).cancelFailedPaymentOrdersBulk();

    }

    @Test
    @DisplayName("결제 실패 취소 처리에서 오류가 발생해면 로그를 남기고 예외를 삼킨다")
    void cancelFailedPayment_catchesException(CapturedOutput output) {
        scheduler.onSaleStarted();

        doThrow(new RuntimeException("DB 에러"))
                .when(orderService).cancelFailedPaymentOrdersBulk();

        assertThatCode(() -> scheduler.processOrderMaintenance())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제 실패 건 취소 처리 실패");
        verify(orderService).expireUnpaidOrdersBulk();
    }

    @Test
    @DisplayName("결제완료 주문 재처리에서 오류가 발생하면 로그를 남기고 예외를 삼킨다")
    void completeStuckOrders_catchesException(CapturedOutput output) {
        scheduler.onSaleStarted();

        doThrow(new RuntimeException())
                .when(orderService).completeStuckOrders();

        assertThatCode(() -> scheduler.processOrderMaintenance())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제완료 주문 재처리 실패");
        verify(orderService).completeStuckOrders();
    }

    @Test
    @DisplayName("모두 다 실패해도 예외를 삼킨다")
    void bothFail_catchesException(CapturedOutput output) {
        scheduler.onSaleStarted();

        doThrow(new RuntimeException("expire 에러"))
                .when(orderService).expireUnpaidOrdersBulk();
        doThrow(new RuntimeException("cancel 에러"))
                .when(orderService).cancelFailedPaymentOrdersBulk();
        doThrow(new RuntimeException("complete 에러"))
                .when(orderService).completeStuckOrders();

        assertThatCode(() -> scheduler.processOrderMaintenance())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 미결제 만료 처리 실패");
        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제 실패 건 취소 처리 실패");
        assertThat(output.getOut()).contains("[ORDER_SCHEDULER] 결제완료 주문 재처리 실패");
    }
}
