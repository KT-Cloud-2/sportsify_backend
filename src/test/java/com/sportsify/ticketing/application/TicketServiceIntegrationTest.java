package com.sportsify.ticketing.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.game.domain.model.Game;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.application.service.TicketService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TicketingTestFixture fixture;

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
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Long orderId = resDto.orderId();

        ticketService.createTickets(orderId, member.getId());

        TicketListResponseDto tickets = ticketService.getMyTickets(member.getId(), 0, 10);
        assertThat(tickets.items()).hasSize(3);
        assertThat(tickets.items())
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo("CONFIRMED");
                    assertThat(item.ticketNumber()).isNotNull();
                    assertThat(item.price()).isEqualTo(10000);
                });
    }

    @Test
    @DisplayName("getMyTickets 호출 시 페이징이 올바르게 적용된다.")
    void getMyTickets_paging() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        ticketService.createTickets(resDto.orderId(), member.getId());

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

        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        ticketService.createTickets(resDto.orderId(), member.getId());

        TicketListResponseDto myTickets = ticketService.getMyTickets(member.getId(), 0, 10);
        TicketListResponseDto otherTickets = ticketService.getMyTickets(otherMember.getId(), 0, 10);

        assertThat(myTickets.items()).hasSize(3);
        assertThat(otherTickets.items()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 createTickets 호출 시 예외가 발생한다.")
    void createTickets_memberNotFound() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);

        assertThatThrownBy(() -> ticketService.createTickets(resDto.orderId(), 9999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 주문으로 createTickets 호출 시 예외가 발생한다.")
    void createTickets_orderNotFound() {
        assertThatThrownBy(() -> ticketService.createTickets(9999L, member.getId()))
                .isInstanceOf(BusinessException.class);
    }
}
