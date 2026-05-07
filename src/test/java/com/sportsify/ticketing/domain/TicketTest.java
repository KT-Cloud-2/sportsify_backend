package com.sportsify.ticketing.domain;

import com.sportsify.member.domain.model.Member;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.model.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TicketTest {

    @Test
    @DisplayName("티켓 생성 시 CONFIRMED 상태로 초기화된다.")
    void createTicket() {

        OrderSeat mockOrderSeat = mock(OrderSeat.class);
        Member mockMember = mock(Member.class);

        Ticket ticket = Ticket.create(mockOrderSeat, mockMember, 20000);

        assertThat(ticket.getOrderSeat()).isEqualTo(mockOrderSeat);
        assertThat(ticket.getMember()).isEqualTo(mockMember);
        assertThat(ticket.getTicketNumber()).isNotNull();
        assertThat(ticket.getPrice()).isEqualTo(20000);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CONFIRMED);
        assertThat(ticket.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("티켓 사용 시 상태와 사용 시간이 업데이트된다.")
    void updateTicketUsed() {

        OrderSeat mockOrderSeat = mock(OrderSeat.class);
        Member mockMember = mock(Member.class);

        Ticket ticket = Ticket.create(mockOrderSeat, mockMember, 20000);

        LocalDateTime usedTime = LocalDateTime.now();
        ticket.updateAsUsed(usedTime);

        assertThat(ticket.getUsedAt()).isEqualTo(usedTime);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
    }

    @Test
    @DisplayName("티켓 취소 시 상태와 취소 시간이 업데이트된다.")
    void updateTicketCancelled() {

        OrderSeat mockOrderSeat = mock(OrderSeat.class);
        Member mockMember = mock(Member.class);

        Ticket ticket = Ticket.create(mockOrderSeat, mockMember, 20000);

        LocalDateTime cancelledTime = LocalDateTime.now();
        ticket.updateAsCancelled(cancelledTime);

        assertThat(ticket.getCancelledAt()).isEqualTo(cancelledTime);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }


//    @Test
//    @DisplayName("주문 좌석 생성 시 지정한 expiresAt으로 초기화된다.")
//    void createOrderSeatWithExpiresAt() {
//
//        Order mockOrder = mock(Order.class);
//        GameSeat mockGameSeat = mock(GameSeat.class);
//
//        LocalDateTime now = LocalDateTime.now();
//        OrderSeat orderSeat = OrderSeat.create(mockOrder, mockGameSeat);
//
//        assertThat(orderSeat.getOrder()).isEqualTo(mockOrder);
//        assertThat(orderSeat.getGameSeat()).isEqualTo(mockGameSeat);
//        assertThat(orderSeat.getStatus()).isEqualTo(OrderSeatStatus.HOLDING);
//        assertThat(orderSeat.getExpiresAt()).isEqualTo(now);
//    }

}