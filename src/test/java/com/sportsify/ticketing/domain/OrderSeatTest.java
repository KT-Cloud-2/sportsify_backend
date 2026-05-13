package com.sportsify.ticketing.domain;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderSeatTest {

    @Test
    @DisplayName("주문 좌석 생성 시 HOLDING 상태로 초기화된다.")
    void createOrderSeat() {

        Order mockOrder = mock(Order.class);
        GameSeat mockGameSeat = mock(GameSeat.class);

        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat);

        assertThat(orderSeat.getOrder()).isEqualTo(mockOrder);
        assertThat(orderSeat.getGameSeat()).isEqualTo(mockGameSeat);
        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.HOLDING);
    }

}
