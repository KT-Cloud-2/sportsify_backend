package com.sportsify.ticketing.application;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.member.domain.model.Member;
import com.sportsify.payment.domain.entity.Payment;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.payment.domain.type.PaymentStatus;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.scheduler.OrderExpirationScheduler;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class OrderExpirationSchedulerTest extends RepositoryTestSupport {
    private Member member;
    private Game game;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private OrderExpirationScheduler scheduler;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        game = fixture.createGame();
    }

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("만료된 주문의 좌석이 AVAILABLE로 복구된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredOrder_seatBecomesAvailable() {
        Long orderId = transactionTemplate.execute(txStatus -> {
            List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

            ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
            ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
            Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

            order.updateExpiresAt(LocalDateTime.now().minusMinutes(1));
            return orderRepository.save(order).getId();
        });

        scheduler.releaseUnpaidOrders();

        transactionTemplate.executeWithoutResult(txStatus -> {
            Order savedOrder = orderRepository.findById(orderId).orElseThrow();
            List<OrderSeat> orderSeats = savedOrder.getOrderSeats();

            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(orderSeats)
                    .extracting(OrderSeat::getStatus)
                    .containsOnly(OrderSeatStatus.EXPIRED);
            assertThat(orderSeats)
                    .extracting(OrderSeat::getGameSeat)
                    .extracting(GameSeat::getSeatStatus)
                    .containsOnly(SeatStatus.AVAILABLE);
        });
    }

    @ParameterizedTest
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("결제 실패/취소/환불한 주문의 좌석이 AVAILABLE로 복구된다")
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELED", "REFUNDED"})
    void payingOrderWithFailedPayment_seatBecomesAvailable(PaymentStatus status) {
        Long orderId = transactionTemplate.execute(txStatus -> {
            List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

            ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
            ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
            Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

            order.updateStatus(OrderStatus.PAYING);
            orderRepository.save(order);

            Payment payment = Payment.builder()
                    .userId(member.getId())
                    .matchId(game.getId())
                    .seatId(gameSeatIds.get(0))
                    .orderId(order.getId())
                    .tossOrderId("TEST_TOSS_ORDER_" + order.getId())
                    .idempotencyKey("TEST_IDEMPOTENCY_" + order.getId())
                    .amount(fixture.TICKET_PRICE * 2L)
                    .paymentMethod("PAY")
                    .status(status)
                    .requestedAt(LocalDateTime.now())
                    .build();

            paymentRepository.save(payment);
            return order.getId();
        });

        scheduler.releaseUnpaidOrders();

        transactionTemplate.executeWithoutResult(txStatus -> {
            Order savedOrder = orderRepository.findById(orderId).orElseThrow();
            List<OrderSeat> orderSeats = savedOrder.getOrderSeats();

            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(orderSeats)
                    .extracting(OrderSeat::getStatus)
                    .containsOnly(OrderSeatStatus.CANCELLED);
            assertThat(orderSeats)
                    .extracting(OrderSeat::getGameSeat)
                    .extracting(GameSeat::getSeatStatus)
                    .containsOnly(SeatStatus.AVAILABLE);
        });
    }

}
