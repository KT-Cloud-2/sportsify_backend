package com.sportsify.ticketing.application;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.payment.domain.entity.Payment;
import com.sportsify.payment.domain.repository.PaymentRepository;
import com.sportsify.payment.domain.type.PaymentStatus;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.scheduler.OrderMaintenanceScheduler;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.*;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.domain.repository.TicketRepository;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class OrderMaintenanceSchedulerTest extends RepositoryTestSupport {
    private Member member;
    private Game game;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private OrderMaintenanceScheduler scheduler;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        game = fixture.createGame();
        scheduler.onSaleStarted();
    }

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
        scheduler.onSaleEnded();
    }

    @Test
    @DisplayName("만료된 주문의 좌석이 AVAILABLE로 복구된다")
    void expiredOrder_seatBecomesAvailable() {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        order.updateExpiresAt(LocalDateTime.now().minusMinutes(1));
        Long orderId = orderRepository.save(order).getId();

        scheduler.processOrderMaintenance();

        Order updatedOrder = orderRepository.findByIdWithAll(orderId).orElseThrow();
        List<OrderSeat> orderSeats = updatedOrder.getOrderSeats();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orderSeats)
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.EXPIRED);
        assertThat(orderSeats)
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.AVAILABLE);
    }

    @ParameterizedTest
    @DisplayName("결제 실패/취소/환불한 주문의 좌석이 AVAILABLE로 복구된다")
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELED", "REFUNDED"})
    void pendingOrderWithFailedPayment_seatBecomesAvailable(PaymentStatus status) {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        Payment payment = Payment.builder()
                .userId(member.getId())
                .matchId(game.getId())
                .seatId(gameSeatIds.get(0))
                .orderId(order.getId())
                .tossOrderId("TEST_TOSS_ORDER_" + order.getId())
                .idempotencyKey("TEST_IDEMPOTENCY_" + order.getId())
                .amount(order.getTotalAmount())
                .paymentMethod("PAY")
                .status(status)
                .requestedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        Long orderId = order.getId();

        scheduler.processOrderMaintenance();

        Order updatedOrder = orderRepository.findByIdWithAll(orderId).orElseThrow();
        List<OrderSeat> orderSeats = updatedOrder.getOrderSeats();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderSeats)
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.CANCELLED);
        assertThat(orderSeats)
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("결제완료면서 미처리된 주문의 좌석이 Confirmed되고 티켓이 생성된다.")
    void pendingOrderWithConfirmedPayment_confirmsOrderAndSeat_createTickets() {
        Long memberId = fixture.createMember("t2@test.com", "n2").getId();
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(memberId, reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        Payment payment = Payment.builder()
                .userId(member.getId())
                .matchId(game.getId())
                .seatId(gameSeatIds.get(0))
                .orderId(order.getId())
                .tossOrderId("TEST_TOSS_ORDER_" + order.getId())
                .idempotencyKey("TEST_IDEMPOTENCY_" + order.getId())
                .amount(order.getTotalAmount())
                .paymentMethod("PAY")
                .status(PaymentStatus.COMPLETED)
                .requestedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        Long orderId = order.getId();

        scheduler.processOrderMaintenance();

        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        Page<Ticket> tickets = ticketRepository.findByMemberId(updatedOrder.getMemberId(), pageable);
        assertThat(tickets.get()).hasSize(updatedOrder.getOrderSeats().size());
        assertThat(tickets.get().allMatch(ticket -> ticket.getStatus() == TicketStatus.CONFIRMED)).isTrue();
    }


    @ParameterizedTest
    @ValueSource(ints = {3, 5, 8})
    @DisplayName("결제가 락 보유 중이면 스케줄러는 SKIP LOCKED로 PendingOrder의 해당 행을 건너뛴다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void paymentLocksFirst_schedulerSkips(int skipCount) throws InterruptedException {
        List<Long> skipIds = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < skipCount * 2; i++) {
                Order order = Order.create(member);
                fixture.createGameSeatsWithCount(game, 1)
                        .forEach(gameSeatId -> {
                            GameSeat gameSeat = gameSeatRepository.getReferenceById(gameSeatId);
                            OrderSeat orderSeat = OrderSeat.create(order, gameSeat, 10000);
                            order.addOrderSeat(orderSeat);
                        });

                order.calculateTotalAmount();
                order.updateExpiresAt(LocalDateTime.now().minusMinutes(1));

                Order saveOrder = orderRepository.save(order);
                if (i < skipCount) skipIds.add(saveOrder.getId());
            }
        });

        CountDownLatch paymentLocked = new CountDownLatch(skipCount);
        CountDownLatch testDone = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(skipCount);

        for (Long skipId : skipIds) {
            executor.submit(() -> {
                transactionTemplate.execute(status -> {
                    orderRepository.findByIdWithLock(skipId);
                    paymentLocked.countDown();

                    try {
                        // Test가 끝날 때까지 Thread A 락 유지
                        testDone.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            });
        }

        // Thread들이 각 skipId를 결제 시작할 때까지(락을 획득할 때까지) 대기
        paymentLocked.await(5, TimeUnit.SECONDS);

        // when - Thread B: 스케줄러 (SKIP LOCKED)
        List<Long> expiredOrderIds = transactionTemplate.execute(status ->
                orderRepository.findExpiredPendingOrderIdsWithoutPayment(LocalDateTime.now())
        );

        // then - 락 걸린 행 skip
        assertThat(expiredOrderIds).doesNotContainAnyElementsOf(skipIds);
        assertThat(expiredOrderIds).hasSize(skipCount);

        testDone.countDown(); // 락 잡은 Thread 해제
        executor.shutdown();
    }

}
