package com.sportsify.ticketing.application;


import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.scheduler.SeatExpirationScheduler;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.model.OrderSeatStatus;
import com.sportsify.ticketing.domain.model.OrderStatus;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import com.sportsify.ticketing.infrastructure.repository.OrderJpaRepository;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationServiceIntegrationTest extends RepositoryTestSupport {
    private Member member;
    private Game game;
    private List<Long> gameSeatIds;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeatExpirationScheduler scheduler;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private GameSeatRepository gameSeatRepository;


    @BeforeEach
    void beforeEach() {
        member = fixture.createMember("t1@test.com", "n1");
        gameSeatIds = fixture.createGameWithSeats();
        game = gameSeatRepository.findById(gameSeatIds.getFirst()).orElseThrow(() -> new RuntimeException("not found game")).getGame();
    }

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("주문 생성 시, 주문의 created_at와 expires_at이 생성된다.")
    void createOrder() {
        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());

        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);

        assertThat(resDto.orderId()).isEqualTo(1L);
        assertThat(resDto.gameId()).isEqualTo(game.getId());
        assertThat(resDto.memberId()).isEqualTo(member.getId());
        assertThat(resDto.seats()).hasSize(gameSeatIds.size());

        Order createdOrder = orderRepository.findById(resDto.orderId()).orElseThrow(() -> new RuntimeException("Order not found"));
        assertThat(createdOrder.getId()).isEqualTo(1L);


        LocalDateTime now = LocalDateTime.now();

        assertThat(createdOrder.getExpiresAt()).isAfter(now).isBefore(now.plusMinutes(16));
        assertThat(createdOrder.getCreatedAt()).isNotNull();

    }

    @Test
    @DisplayName("동시에 같은 자리를 선점하는 요청이 들어올 때, 더 빠른 요청이 좌석을 선점한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReservation_onlyOneSucceeds() throws InterruptedException {

        Member member2 = fixture.createMember("t2@test.com", "n2");
        Member member3 = fixture.createMember("t3@test.com", "n3");

        List<ReservationSeatsRequestDto> requests = List.of(
                ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId()),
                ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member2.getId()),
                ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member3.getId())
        );


        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (ReservationSeatsRequestDto req : requests) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.reserveSeat(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("만료된 주문의 좌석이 AVAILABLE로 복구된다")
    void expiredOrder_seatBecomesAvailable() {

        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow(() -> new RuntimeException("not found order"));

        order.updateExpiresAt(order.getExpiresAt().minusMinutes(16));

        scheduler.expireReservedSeats();

        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow(() -> new RuntimeException("not found order"));
        List<OrderSeat> orderSeats = savedOrder.getOrderSeats();

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        assertThat(orderSeats)
                .extracting(OrderSeat::getStatus)
                .containsOnly(OrderSeatStatus.EXPIRED);

        assertThat(orderSeats)
                .extracting(OrderSeat::getGameSeat)
                .extracting(GameSeat::getSeatStatus)
                .containsOnly(SeatStatus.AVAILABLE);

    }

}
