package com.sportsify.ticketing.domain;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.model.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketTest {

    @Test
    @DisplayName("Ticket 생성 시 CONFIRMED 상태와 ticketNumber가 설정된다.\"")
    void createTicket() {

        OrderSeat mockOrderSeat = mock(OrderSeat.class);
        Member mockMember = mock(Member.class);

        Ticket ticket = Ticket.create(mockOrderSeat, mockMember, 20000);

        assertThat(ticket.getOrderSeat()).isEqualTo(mockOrderSeat);
        assertThat(ticket.getMember()).isEqualTo(mockMember);
        assertThat(ticket.getTicketNumber()).isNotNull();
        assertThat(ticket.getPrice()).isEqualTo(20000);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CONFIRMED);
        assertThat(ticket.getTicketNumber()).hasSize(36);
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


    @Test
    @DisplayName("디미터 위임 메서드가 올바르게 동작한다.")
    void delegationMethods() {
        OrderSeat orderSeat = mock(OrderSeat.class);
        Member member = Member.create("test@test.com", "닉네임", OAuthProvider.GOOGLE, "g-1");

        when(orderSeat.getGameId()).thenReturn(1L);
        when(orderSeat.getSportType()).thenReturn("BASEBALL");
        when(orderSeat.getHomeTeamName()).thenReturn("두산");
        when(orderSeat.getAwayTeamName()).thenReturn("LG");
        when(orderSeat.getStadiumName()).thenReturn("잠실");
        when(orderSeat.getSeatGradeName()).thenReturn("VIP");
        when(orderSeat.getSectionName()).thenReturn("A구역");
        when(orderSeat.getSeatNumber()).thenReturn("12");

        Ticket ticket = Ticket.create(orderSeat, member, 15000);

        assertThat(ticket.getGameId()).isEqualTo(1L);
        assertThat(ticket.getSportType()).isEqualTo("BASEBALL");
        assertThat(ticket.getHomeTeamName()).isEqualTo("두산");
        assertThat(ticket.getAwayTeamName()).isEqualTo("LG");
        assertThat(ticket.getStadium()).isEqualTo("잠실");
        assertThat(ticket.getSeatGrade()).isEqualTo("VIP");
        assertThat(ticket.getSeatSection()).isEqualTo("A구역");
        assertThat(ticket.getSeatNumber()).isEqualTo("12");
        assertThat(ticket.getStatusName()).isEqualTo("CONFIRMED");
    }

}
