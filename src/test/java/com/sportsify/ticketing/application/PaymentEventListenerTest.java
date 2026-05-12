package com.sportsify.ticketing.application;

import com.sportsify.common.event.PaymentCancelledEvent;
import com.sportsify.common.event.PaymentCompletedEvent;
import com.sportsify.common.event.PaymentFailedEvent;
import com.sportsify.common.event.PaymentStartedEvent;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventListenerTest extends RepositoryTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    private Member member;
    private Game game;
    private List<Long> gameSeatIds;

    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        gameSeatIds = fixture.createGameWithSeats();
        game = gameSeatRepository.findById(gameSeatIds.getFirst()).orElseThrow(() -> new RuntimeException("not found game")).getGame();
    }

    @Test
    @DisplayName("결제에 도입 시 주문 상태가 PAYING으로 변경된다.")
    void onStartedPaymentEvent() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        eventPublisher.publishEvent(new PaymentStartedEvent(order.getId()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYING);
    }

    @Test
    @DisplayName("결제가 성공했을 때 주문과 좌석이 확정된다.")
    void onSuccessPaymentEvent() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        eventPublisher.publishEvent(new PaymentCompletedEvent(order.getId()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getExpiresAt()).isNull();
        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.CONFIRMED);

        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.SOLD);

    }

    @Test
    @DisplayName("결제가 취소됐을 때 주문이 취소되고 좌석이 반환된다.")
    void onCancelledPaymentEvent() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        eventPublisher.publishEvent(new PaymentCancelledEvent(order.getId()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(updatedOrder.getExpiresAt()).isNull();
        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.CANCELLED);

        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.AVAILABLE);

    }

    @Test
    @DisplayName("결제가 실패했을 때 주문의 유효시간이 15분 늘어난다.")
    void onFailedPaymentEvent() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();
        eventPublisher.publishEvent(new PaymentStartedEvent(order.getId()));

        LocalDateTime failedAt = LocalDateTime.now();
        eventPublisher.publishEvent(new PaymentFailedEvent(order.getId(), failedAt));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYING);
        assertThat(updatedOrder.getExpiresAt()).isEqualTo(failedAt.plusMinutes(15));
        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.HOLDING);

        assertThat(updatedOrder.getOrderSeats())
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.RESERVED);
    }
}
