package com.sportsify.ticketing.application;


import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationServiceIntegrationTest extends RepositoryTestSupport {
    private Member member;
    private Game game;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeatExpirationScheduler scheduler;

    @Autowired
    private TicketingTestFixture fixture;


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
    @DisplayName("주문 생성 시, 주문의 created_at와 expires_at이 생성된다.")
    void createOrder() {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());

        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);

        assertThat(resDto.gameId()).isEqualTo(game.getId());
        assertThat(resDto.memberId()).isEqualTo(member.getId());
        assertThat(resDto.seats()).hasSize(gameSeatIds.size());

        Order createdOrder = orderRepository.findById(resDto.orderId()).orElseThrow();

        LocalDateTime now = LocalDateTime.now();

        assertThat(createdOrder.getExpiresAt()).isAfter(now).isBefore(now.plusMinutes(16));
        assertThat(createdOrder.getCreatedAt()).isNotNull();

    }

    @ParameterizedTest
    @DisplayName("동시에 같은 좌석을 요청할 때, 1명만 성공한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CsvSource({
            "3, 10",
            "5, 50",
            "10, 100",
            "10, 200"
    })
    void concurrentReservation_onlyOneSucceeds(int users, int threadCount) throws InterruptedException {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 1);

        ArrayList<Member> members = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            members.add(fixture.createMemberWithNum(i));
        }

        Random random = new Random();

        for (int i = 0; i < threadCount; i++) {
            int value = random.nextInt(users);
            requests.add(ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, members.get(value).getId()));
        }

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
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @ParameterizedTest
    @DisplayName("동시에 다른 좌석을 요청할 때, 전부 성공한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CsvSource({
            "10, 10",
            "50, 50",
            "100, 100",
            "200, 200"
    })
    void concurrentReservation_allSucceeds(int users, int threadCount) throws InterruptedException {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, threadCount);

        ArrayList<Member> members = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            members.add(fixture.createMemberWithNum(i));
        }

        for (int i = 0; i < threadCount; i++) {
            requests.add(ReservationSeatsRequestDto.from(game.getId(), List.of(gameSeatIds.get(i)), members.get(i).getId()));
        }

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

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @ParameterizedTest
    @DisplayName("동시에 일부 겹치는 요청이 들어왔을 때, 한쪽만 성공한다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CsvSource({
            "4, 20",
            "6, 30",
            "10, 100",
            "20, 200"
    })
    void concurrentReservation_halfSucceeds(int users, int threadCount) throws InterruptedException {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, threadCount);

        ArrayList<Member> members = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            members.add(fixture.createMemberWithNum(i));
        }

        Random random = new Random();

        for (int i = 0; i < threadCount / 2; i++) {
            int value = random.nextInt(users);
            requests.add(ReservationSeatsRequestDto.from(game.getId(), List.of(gameSeatIds.get(i * 2), gameSeatIds.get(i * 2 + 1)), members.get(value).getId()));
            requests.add(ReservationSeatsRequestDto.from(game.getId(), List.of(gameSeatIds.get(i * 2)), members.get(value).getId()));
        }

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

        assertThat(successCount.get()).isEqualTo(threadCount / 2);
        assertThat(failCount.get()).isEqualTo(threadCount / 2);
    }


    @Test
    @DisplayName("만료된 주문의 좌석이 AVAILABLE로 복구된다")
    void expiredOrder_seatBecomesAvailable() {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = ReservationSeatsRequestDto.from(game.getId(), gameSeatIds, member.getId());
        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(reqDto);
        Order order = orderRepository.findById(resDto.orderId()).orElseThrow();

        order.updateExpiresAt(order.getExpiresAt().minusMinutes(16));

        scheduler.expireReservedSeats();

        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
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
