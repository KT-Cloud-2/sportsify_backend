package com.sportsify.ticketing.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.listener.PaymentEventListener;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.*;
import com.sportsify.ticketing.fixture.PaymentEventListenerTestFixture;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.infrastructure.repository.TicketJpaRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@EnableAsync
@ExtendWith(OutputCaptureExtension.class)
@Slf4j
class PaymentEventListenerTest extends RepositoryTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private TicketJpaRepository ticketRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private PaymentEventListenerTestFixture eventFixture;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private DataSource dataSource;

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
    @DisplayName("결제 완료 이벤트 수신 시, DB에 저장된 주문이어야 한다.")
    void onSuccessPaymentEvent_orderNotFound() {
        assertThatThrownBy(() ->
                paymentEventListener.onPaymentSuccess(
                        eventFixture.createCompletedEventByOrderId(-1L, member.getId())

                ))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }


    @ParameterizedTest
    @DisplayName("결제 완료 이벤트 수신 시, 주문 상태가 PENDING이어야 한다.")
    @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "CANCELLED"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_isPaying(OrderStatus status, CapturedOutput output) {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.updateStatus(status);

            eventPublisher.publishEvent(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        });

        Order result = orderRepository.findById(orderId).orElseThrow();

        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(output.getOut()).contains("결제 완료 불가 상태");
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시, 주문과 좌석이 확정되고 티켓이 생성된다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_success() {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        transactionTemplate.executeWithoutResult(s -> {
            eventPublisher.publishEvent(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        });
        transactionTemplate.executeWithoutResult(s -> {
            List<Ticket> tickets = ticketRepository.findAll();
            Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
            List<Long> orderSeatIds = updatedOrder.getOrderSeats().stream().map(OrderSeat::getId).toList();

            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(updatedOrder.getExpiresAt()).isNull();

            assertThat(updatedOrder.getOrderSeats())
                    .extracting(OrderSeat::getStatus)
                    .containsOnly(OrderSeatStatus.CONFIRMED);

            assertThat(updatedOrder.getOrderSeats())
                    .extracting(OrderSeat::getGameSeat)
                    .extracting(GameSeat::getSeatStatus)
                    .containsOnly(SeatStatus.SOLD);

            assertThat(tickets).hasSize(gameSeatIds.size());
            assertThat(tickets)
                    .extracting(ticket -> ticket.getOrderSeat().getId())
                    .containsExactlyInAnyOrderElementsOf(orderSeatIds);

            assertThat(tickets)
                    .extracting(Ticket::getStatus)
                    .containsOnly(TicketStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시, 풀 고갈이면 5초 이내로 실패한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_poolExhaustionFails_within5s() throws SQLException {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        HikariDataSource hikariDS = (HikariDataSource) dataSource;
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < hikariDS.getMaximumPoolSize(); i++) {
            held.add(hikariDS.getConnection());
        }

        long start = System.currentTimeMillis();
        try {
            paymentEventListener.onPaymentSuccess(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isBetween(4_000L, 6_000L);
        } finally {
            held.forEach(c -> {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시, 락 대기가 발생하면 트랜잭션 타임아웃으로 5초 이내 실패한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onSuccessPaymentEvent_lockContention_under5s() throws Exception {
        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        Long orderId = reservationService.reserveSeat(member.getId(), reqDto).orderId();

        HikariDataSource hikariDS = (HikariDataSource) dataSource;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try (Connection conn = hikariDS.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM orders WHERE id = ? FOR UPDATE")) {
                    stmt.setLong(1, orderId);
                    stmt.executeQuery();
                    lockAcquired.countDown();

                    testDone.await(30, TimeUnit.SECONDS);
                }
                conn.rollback();
            } catch (Exception e) {
                log.error("락 스레드 에러", e);
            }
        });

        boolean acquired = lockAcquired.await(5, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();

        long start = System.currentTimeMillis();

        try {
            paymentEventListener.onPaymentSuccess(eventFixture.createCompletedEventByOrderId(orderId, member.getId()));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(5_000);
        } finally {
            testDone.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("결제 취소 이벤트 수신 시, DB에 저장된 주문이어야 한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onCancelledPaymentEvent_orderNotFound() {
        assertThatThrownBy(() ->
                paymentEventListener.onPaymentCancelled(
                        eventFixture.createCancelledEventByOrderId(-1L, member.getId())

                ))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
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
