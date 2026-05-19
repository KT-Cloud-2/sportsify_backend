package com.sportsify.ticketing.domain;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 PENDING 상태로 초기화된다.")
    void createOrderWithPendingStatus() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");

        LocalDateTime before = LocalDateTime.now().plusMinutes(15).minusSeconds(1);
        Order order = Order.create(member);
        LocalDateTime after = LocalDateTime.now().plusMinutes(15).plusSeconds(1);

        assertThat(order.getMember()).isEqualTo(member);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderSeats().size()).isEqualTo(0);
        assertThat(order.getExpiresAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("addOrderSeat 호출 시 주문 좌석이 추가된다.")
    public void addOrderSeat() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        OrderSeat mockOrderSeat = mock(OrderSeat.class);

        order.addOrderSeat(mockOrderSeat);

        assertThat(order.getOrderSeats()).hasSize(1);
        assertThat(order.getOrderSeats()).contains(mockOrderSeat);
    }

    @Test
    @DisplayName("expires 호출 시 주문 상태가 EXPIRED로 변경된다.")
    void changeStatusWhenExpire() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        OrderSeat mockOrderSeat = mock(OrderSeat.class);

        order.addOrderSeat(mockOrderSeat);

        order.expire();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(mockOrderSeat, times(1)).expire();

    }

    @Test
    @DisplayName("cancel 호출 시 주문 상태가 CANCELLED로 변경된다.")
    void changeStatusWhenCancel() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        OrderSeat mockOrderSeat = mock(OrderSeat.class);

        order.addOrderSeat(mockOrderSeat);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(mockOrderSeat, times(1)).cancel();

    }

}
