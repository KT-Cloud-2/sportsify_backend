package com.sportsify.ticketing.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.ticketing.application.service.TicketService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.Ticket;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @InjectMocks
    private TicketService ticketService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Test
    @DisplayName("createTickets 호출 시 OrderSeat 수만큼 티켓이 생성된다.")
    void createTickets_success() {
        Long orderId = 1L;
        Long memberId = 1L;

        Member member = mock(Member.class);
        Order order = mock(Order.class);
        OrderSeat orderSeat1 = mock(OrderSeat.class);
        OrderSeat orderSeat2 = mock(OrderSeat.class);

        when(orderSeat1.getPrice()).thenReturn(10000);
        when(orderSeat2.getPrice()).thenReturn(15000);

        when(orderRepository.findByIdWithOrderSeats(orderId)).thenReturn(Optional.of(order));
        when(order.getMemberId()).thenReturn(memberId);
        when(order.getOrderSeats()).thenReturn(List.of(orderSeat1, orderSeat2));
        when(memberRepository.getReferenceById(memberId)).thenReturn(member);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketService.createTickets(orderId, memberId);

        verify(ticketRepository, times(2)).save(any(Ticket.class));
    }


    @Test
    @DisplayName("createTickets 호출 시 요청자와 주문자가 불일치하면 예외가 발생한다.")
    void createTickets_memberMismatch() {
        Long orderId = 1L;
        Long memberId = 1L;
        Long otherMemberId = 2L;

        Order order = mock(Order.class);

        when(orderRepository.findByIdWithOrderSeats(orderId)).thenReturn(Optional.of(order));
        when(order.getMemberId()).thenReturn(otherMemberId);

        assertThatThrownBy(() -> ticketService.createTickets(orderId, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_MEMBER_MISMATCH);
    }

    @Test
    @DisplayName("createTickets 호출 시 존재하지 않는 주문이면 예외가 발생한다.")
    void createTickets_orderNotFound() {
        when(orderRepository.findByIdWithOrderSeats(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTickets(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("getMyTickets 호출 시 페이징된 결과를 반환한다.")
    void getMyTickets_success() {
        Long memberId = 1L;
        Ticket ticket1 = mock(Ticket.class);
        Ticket ticket2 = mock(Ticket.class);

        when(ticket1.getId()).thenReturn(1L);
        when(ticket1.getTicketNumber()).thenReturn("uuid-1");
        when(ticket1.getGameId()).thenReturn(1L);
        when(ticket1.getSportType()).thenReturn("BASEBALL");
        when(ticket1.getHomeTeamName()).thenReturn("두산");
        when(ticket1.getAwayTeamName()).thenReturn("LG");
        when(ticket1.getStartAt()).thenReturn(null);
        when(ticket1.getStadium()).thenReturn("잠실");
        when(ticket1.getSeatGrade()).thenReturn("VIP");
        when(ticket1.getSeatSection()).thenReturn("A구역");
        when(ticket1.getSeatNumber()).thenReturn("1");
        when(ticket1.getPrice()).thenReturn(10000);
        when(ticket1.getStatusName()).thenReturn("CONFIRMED");
        when(ticket1.getIssuedAt()).thenReturn(null);

        when(ticket2.getId()).thenReturn(2L);
        when(ticket2.getTicketNumber()).thenReturn("uuid-2");
        when(ticket2.getGameId()).thenReturn(1L);
        when(ticket2.getSportType()).thenReturn("BASEBALL");
        when(ticket2.getHomeTeamName()).thenReturn("두산");
        when(ticket2.getAwayTeamName()).thenReturn("LG");
        when(ticket2.getStartAt()).thenReturn(null);
        when(ticket2.getStadium()).thenReturn("잠실");
        when(ticket2.getSeatGrade()).thenReturn("VIP");
        when(ticket2.getSeatSection()).thenReturn("A구역");
        when(ticket2.getSeatNumber()).thenReturn("2");
        when(ticket2.getPrice()).thenReturn(15000);
        when(ticket2.getStatusName()).thenReturn("CONFIRMED");
        when(ticket2.getIssuedAt()).thenReturn(null);

        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<Ticket> ticketPage = new PageImpl<>(List.of(ticket1, ticket2), pageable, 2);

        when(ticketRepository.findByMemberId(memberId, pageable)).thenReturn(ticketPage);

        var response = ticketService.getMyTickets(memberId, 0, 10);

        assertThat(response.items()).hasSize(2);
        assertThat(response.currentPage()).isEqualTo(0);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("getMyTickets 호출 시 티켓이 없으면 빈 리스트를 반환한다.")
    void getMyTickets_empty() {
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<Ticket> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(ticketRepository.findByMemberId(memberId, pageable)).thenReturn(emptyPage);

        var response = ticketService.getMyTickets(memberId, 0, 10);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.hasNext()).isFalse();
    }
}
