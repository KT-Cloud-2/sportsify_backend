package com.sportsify.ticketing.application;

import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.ticketing.application.service.OrderService;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.OrderSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderSeatRepository orderSeatRepository;
    @Mock
    private GameSeatRepository gameSeatRepository;

    @Test
    @DisplayName("만료된 주문이 없을 때 그대로 return한다.")
    void return_notFoundExpiredOrder() {
        when(orderRepository.findExpiredPendingOrderIdsWithoutPayment(any())).thenReturn(List.of());

        orderService.expireUnpaidOrdersBulk();
        verify(gameSeatRepository, never()).bulkReleaseGameSeatsByOrderIds(anyList());
    }

    @Test
    @DisplayName("만료된 주문이 있을 때 로그가 출력되고 좌석 선점이 풀린다.")
    void success_bulkUpdateExpireOrders(CapturedOutput output) {
        when(orderRepository.findExpiredPendingOrderIdsWithoutPayment(any())).thenReturn(List.of(1L));

        orderService.expireUnpaidOrdersBulk();

        assertThat(output.getOut()).contains("Unpaid Bulk size: 1");
        verify(gameSeatRepository).bulkReleaseGameSeatsByOrderIds(anyList());
        verify(orderSeatRepository).bulkUpdateOrderSeats(anyList(), any());
        verify(orderRepository).bulkUpdateOrders(anyList(), any(), any());
    }


    @Test
    @DisplayName("실패된 주문이 없을 때 그대로 return한다.")
    void return_notFoundFailedOrder() {
        when(orderRepository.findPayingOrderIdsWithFailedPayment()).thenReturn(List.of());

        orderService.cancelFailedPaymentOrdersBulk();
        verify(gameSeatRepository, never()).bulkReleaseGameSeatsByOrderIds(anyList());
    }

    @Test
    @DisplayName("실패된 주문이 있을 때 로그가 출력되고 좌석 선점이 풀린다.")
    void success_bulkUpdateFailedOrders(CapturedOutput output) {
        when(orderRepository.findPayingOrderIdsWithFailedPayment()).thenReturn(List.of(1L));

        orderService.cancelFailedPaymentOrdersBulk();

        assertThat(output.getOut()).contains("Failed Bulk size: 1");
        verify(gameSeatRepository).bulkReleaseGameSeatsByOrderIds(anyList());
        verify(orderSeatRepository).bulkUpdateOrderSeats(anyList(), any());
        verify(orderRepository).bulkUpdateOrders(anyList(), any(), any());
    }

    @Test
    @DisplayName("releaseSeatsBulk가 정상 동작한다.")
    void releaseSeatsBulk() {
        List<Long> orderIds = List.of(1L, 2L);

        LocalDateTime now = LocalDateTime.now();
        orderService.releaseSeatsBulk(orderIds, now, OrderSeatStatus.EXPIRED, OrderStatus.EXPIRED);

        verify(gameSeatRepository).bulkReleaseGameSeatsByOrderIds(orderIds);
        verify(orderSeatRepository).bulkUpdateOrderSeats(orderIds, OrderSeatStatus.EXPIRED);
        verify(orderRepository).bulkUpdateOrders(orderIds, OrderStatus.EXPIRED, now);
    }
}

