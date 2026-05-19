package com.sportsify.ticketing.domain;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderSeatTest {

    @Test
    @DisplayName("주문 좌석 생성 시 HOLDING 상태로 초기화된다.")
    void createOrderSeat() {

        Order mockOrder = mock(Order.class);
        GameSeat mockGameSeat = mock(GameSeat.class);

        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat, 10000);

        assertThat(orderSeat.getOrder()).isEqualTo(mockOrder);
        assertThat(orderSeat.getGameSeat()).isEqualTo(mockGameSeat);
        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.HOLDING);
        assertThat(orderSeat.getPrice()).isEqualTo(10000);
    }

    @Test
    @DisplayName("expires 호출 시 주문좌석 상태가 EXPIRED로 변경된다.")
    void changeStatusWhenExpire() {
        GameSeat mockGameSeat = mock(GameSeat.class);
        Order mockOrder = mock(Order.class);

        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat, 10000);

        orderSeat.expire();

        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.EXPIRED);
        verify(mockGameSeat, times(1)).release();
    }


    @Test
    @DisplayName("cancel 호출 시 주문좌석 상태가 CANCELLED로 변경된다.")
    void changeStatusWhenCancel() {
        GameSeat mockGameSeat = mock(GameSeat.class);
        Order mockOrder = mock(Order.class);

        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat, 10000);

        orderSeat.cancel();

        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.CANCELLED);
        verify(mockGameSeat, times(1)).release();
    }

}
