package com.sportsify.ticketing.application;

import com.sportsify.game.domain.model.Game;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.application.service.TicketService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import com.sportsify.ticketing.presentation.dto.TicketListResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketServiceIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private OrderRepository orderRepository;

    private Member member;
    private Game game;
    private List<Long> gameSeatIds;

    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        game = fixture.createGame();
        gameSeatIds = fixture.createGameSeatsWithCount(game, 3);
    }

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("createTickets 호출 시 OrderSeat 수만큼 티켓이 생성된다.")
    void createTickets_success() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Order order = orderRepository.findByIdWithAll(resDto.orderId()).orElseThrow(RuntimeException::new);

        ticketService.createTickets(order);

        TicketListResponseDto tickets = ticketService.getMyTickets(member.getId(), 0, 10);
        assertThat(tickets.items()).hasSize(3);
        assertThat(tickets.items())
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo("CONFIRMED");
                    assertThat(item.ticketNumber()).isNotNull();
                    assertThat(item.price()).isEqualTo(15000);
                });
    }

    @Test
    @DisplayName("getMyTickets 호출 시 페이징이 올바르게 적용된다.")
    void getMyTickets_paging() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Order order = orderRepository.findByIdWithAll(resDto.orderId()).orElseThrow(RuntimeException::new);

        ticketService.createTickets(order);

        TicketListResponseDto page0 = ticketService.getMyTickets(member.getId(), 0, 2);
        TicketListResponseDto page1 = ticketService.getMyTickets(member.getId(), 1, 2);

        assertThat(page0.items()).hasSize(2);
        assertThat(page0.currentPage()).isEqualTo(0);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page0.totalCount()).isEqualTo(3);

        assertThat(page1.items()).hasSize(1);
        assertThat(page1.currentPage()).isEqualTo(1);
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 티켓은 조회되지 않는다.")
    void getMyTickets_isolation() {
        Member otherMember = fixture.createMember("other@test.com", "other");

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Order order = orderRepository.findByIdWithAll(resDto.orderId()).orElseThrow(RuntimeException::new);

        ticketService.createTickets(order);

        TicketListResponseDto myTickets = ticketService.getMyTickets(member.getId(), 0, 10);
        TicketListResponseDto otherTickets = ticketService.getMyTickets(otherMember.getId(), 0, 10);

        assertThat(myTickets.items()).hasSize(3);
        assertThat(otherTickets.items()).isEmpty();
    }

}
