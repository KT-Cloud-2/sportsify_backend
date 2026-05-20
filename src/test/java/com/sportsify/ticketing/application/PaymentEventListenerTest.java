package com.sportsify.ticketing.application;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.*;
import com.sportsify.ticketing.fixture.PaymentEventListenerTestFixture;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.TicketJpaRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableAsync
@ExtendWith(OutputCaptureExtension.class)
class PaymentEventListenerTest extends RepositoryTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private TicketJpaRepository ticketRepository;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private PaymentEventListenerTestFixture eventFixture;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Member member;
    private Game game;
    private List<Long> gameSeatIds;

    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        game = fixture.createGame();
        gameSeatIds = fixture.createGameSeatsWithCount(game, 2);
    }

    @AfterEach
    void afterEach() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("결제 도입 이벤트 수신 시, 주문 상태가 PENDING이어야 한다.")
    void onStartedPaymentEvent_isPending(CapturedOutput output) {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        Order order = orderRepository.findById(orderId).orElseThrow();
        order.updateStatus(OrderStatus.CONFIRMED);

        eventPublisher.publishEvent(eventFixture.createStartedEventByOrderId(orderId, member.getId()));

        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();

        assertThat(updatedOrder.getStatus()).isNotEqualTo(OrderStatus.PAYING);
        assertThat(output.getOut()).contains("결제 시작 불가 상태:");
    }

    @Test
    @DisplayName("결제 도입 이벤트 수신 시, 주문 상태가 PAYING으로 변경된다.")
    void onStartedPaymentEvent() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        eventPublisher.publishEvent(eventFixture.createStartedEventByOrderId(orderId, member.getId()));

        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYING);
    }

    @ParameterizedTest
    @DisplayName("결제 완료 이벤트 수신 시, 주문 상태가 PAYING이어야 한다.")
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "CANCELLED"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_isPaying(OrderStatus status, CapturedOutput output) {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.updateStatus(status);

            eventPublisher.publishEvent(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        });

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order result = orderRepository.findById(orderId).orElseThrow();

                    assertThat(result.getExpiresAt()).isNotNull();
                    assertThat(output.getOut()).contains("결제 완료 불가 상태");
                });
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시, 주문과 좌석이 확정된다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            eventPublisher.publishEvent(eventFixture.createStartedEventByOrderId(orderId, member.getId()));
            eventPublisher.publishEvent(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        });

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
                        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                        assertThat(updatedOrder.getExpiresAt()).isNull();

                        assertThat(updatedOrder.getOrderSeats())
                                .extracting(OrderSeat::getStatus)
                                .containsOnly(OrderSeatStatus.CONFIRMED);

                        assertThat(updatedOrder.getOrderSeats())
                                .extracting(OrderSeat::getGameSeat)
                                .extracting(GameSeat::getSeatStatus)
                                .containsOnly(SeatStatus.SOLD);
                    });
                });
    }


    @Test
    @DisplayName("결제 완료 이벤트 수신 시, 좌석별 티켓이 생성된다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_createTickets() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            eventPublisher.publishEvent(eventFixture.createStartedEventByOrderId(orderId, member.getId()));
            eventPublisher.publishEvent(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        });


        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        List<Ticket> tickets = ticketRepository.findAll();
                        Order order = orderRepository.findByIdWithOrderSeats(orderId).orElseThrow();
                        List<Long> orderSeatIds = order.getOrderSeats().stream().map(OrderSeat::getId).toList();
                        assertThat(tickets).hasSize(gameSeatIds.size());
                        assertThat(tickets)
                                .extracting(ticket -> ticket.getOrderSeat().getId())
                                .containsExactlyInAnyOrderElementsOf(orderSeatIds);

                        assertThat(tickets)
                                .extracting(ticket -> ticket.getStatus())
                                .containsOnly(TicketStatus.CONFIRMED);

                    });
                });

    }

    @ParameterizedTest
    @DisplayName("결제 취소 이벤트 수신 시, 주문 상태가 PAYING이나 PENDING이어야 한다.")
    @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "CANCELLED"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onCancelledPaymentEvent_isPayingOrPending(OrderStatus status, CapturedOutput output) {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.updateStatus(status);

            eventPublisher.publishEvent(eventFixture.createCancelledEventByOrderId(orderId, member.getId()));
        });

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order result = orderRepository.findById(orderId).orElseThrow();

                    assertThat(result.getExpiresAt()).isNotNull();
                    assertThat(output.getOut()).contains("orderId=");
                });
    }

    @Test
    @DisplayName("결제 취소 이벤트 수신 시, 주문이 취소되고 좌석이 반환된다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onCancelledPaymentEvent() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            eventPublisher.publishEvent(eventFixture.createStartedEventByOrderId(orderId, member.getId()));
            eventPublisher.publishEvent(eventFixture.createCancelledEventByOrderId(orderId, member.getId()));
        });

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    transactionTemplate.executeWithoutResult(s -> {
                        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();

                        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                        assertThat(updatedOrder.getExpiresAt()).isNull();

                        assertThat(updatedOrder.getOrderSeats())
                                .extracting(OrderSeat::getStatus)
                                .containsOnly(OrderSeatStatus.CANCELLED);

                        assertThat(updatedOrder.getOrderSeats())
                                .extracting(OrderSeat::getGameSeat)
                                .extracting(GameSeat::getSeatStatus)
                                .containsOnly(SeatStatus.AVAILABLE);
                    });
                });
    }
}
