package com.sportsify.ticketing.domain;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderSeatTest {

    @Test
    @DisplayName("주문 좌석 생성 시 HOLDING 상태로 초기화된다.")
    void createOrderSeat() {

        Order mockOrder = mock(Order.class);
        GameSeat mockGameSeat = mock(GameSeat.class);

        LocalDateTime before = LocalDateTime.now().plusMinutes(15).minusSeconds(1);
        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat);
        LocalDateTime after = LocalDateTime.now().plusMinutes(15).plusSeconds(1);

        assertThat(orderSeat.getOrder()).isEqualTo(mockOrder);
        assertThat(orderSeat.getGameSeat()).isEqualTo(mockGameSeat);
        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.HOLDING);
        assertThat(orderSeat.getExpiresAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("요청한 expiresAt으로 업데이트 된다.")
    void createOrderSeatWithExpiresAt() {

        Order mockOrder = mock(Order.class);
        GameSeat mockGameSeat = mock(GameSeat.class);

        LocalDateTime now = LocalDateTime.now();
        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat);

        orderSeat.updateExpiresAt(now);

        assertThat(orderSeat.getExpiresAt()).isEqualTo(now);
    }
}
