package com.sportsify.ticketing.domain;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 PENDING 상태로 초기화된다.")
    void order_createOrderWithPendingStatus() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");

        LocalDateTime before = LocalDateTime.now().plusMinutes(10).minusSeconds(1);
        Order order = Order.create(member);
        LocalDateTime after = LocalDateTime.now().plusMinutes(10).plusSeconds(1);

        assertThat(order.getMember()).isEqualTo(member);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderSeats().size()).isEqualTo(0);
        assertThat(order.getExpiresAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("addOrderSeat 호출 시 주문 좌석이 추가된다.")
    void order_addOrderSeat() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        OrderSeat mockOrderSeat = mock(OrderSeat.class);

        order.addOrderSeat(mockOrderSeat);

        assertThat(order.getOrderSeats()).hasSize(1);
        assertThat(order.getOrderSeats()).contains(mockOrderSeat);
    }

    @Test
    @DisplayName("calculateTotalAmount 호출 시 주문의 최종금액이 업데이트된다.")
    void order_calculateTotalAmount() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        OrderSeat mockOrderSeat1 = mock(OrderSeat.class);
        OrderSeat mockOrderSeat2 = mock(OrderSeat.class);
        OrderSeat mockOrderSeat3 = mock(OrderSeat.class);

        when(mockOrderSeat1.getPrice()).thenReturn(10);
        when(mockOrderSeat2.getPrice()).thenReturn(20);
        when(mockOrderSeat3.getPrice()).thenReturn(30);

        order.addOrderSeat(mockOrderSeat1);
        order.addOrderSeat(mockOrderSeat2);
        order.addOrderSeat(mockOrderSeat3);

        order.calculateTotalAmount();

        assertThat(order.getTotalAmount()).isEqualTo(60);
    }

    @Test
    @DisplayName("getMemberId 호출 시 member의 Id를 반환한다.")
    void order_getMemberId() {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        assertThat(order.getMemberId()).isEqualTo(member.getId());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED"})
    @DisplayName("isClosed 호출 시 order의 상태가 CANCELLED 혹은 EXPIRED이면 true를 반환한다.")
    void order_isClosed_true(OrderStatus status) {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        order.updateStatus(status);

        assertThat(order.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED"})
    @DisplayName("isClosed 호출 시 order의 상태가 CANCELLED 혹은 EXPIRED이 아니면 false를 반환한다.")
    void order_isClosed_false(OrderStatus status) {
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");
        Order order = Order.create(member);

        order.updateStatus(status);

        assertThat(order.isClosed()).isFalse();
    }
}
