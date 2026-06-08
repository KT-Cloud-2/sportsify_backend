package com.sportsify.ticketing.application;


import com.sportsify.game.domain.model.Game;
import com.sportsify.member.domain.model.Member;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.application.service.ReservationService;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderConstants;
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
    @DisplayName("주문 생성 시, 주문의 created_at과 expires_at이 생성되고 amount가 올바르게 계산된다.")
    void createOrder() {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 2);

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);

        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);

        assertThat(resDto.gameId()).isEqualTo(game.getId());
        assertThat(resDto.memberId()).isEqualTo(member.getId());
        assertThat(resDto.seats()).hasSize(gameSeatIds.size());
        assertThat(resDto.amount()).isGreaterThan(0L);

        Order createdOrder = orderRepository.findById(resDto.orderId()).orElseThrow();

        LocalDateTime now = LocalDateTime.now();

        assertThat(createdOrder.getExpiresAt()).isAfter(now).isBefore(now.plusMinutes(OrderConstants.EXPIRATION_MINUTES));
        assertThat(createdOrder.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 생성 시, amount는 각 좌석 가격의 합계와 일치한다.")
    void createOrder_amountEqualsSum() {
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 3);

        ReservationSeatsRequestDto reqDto = new ReservationSeatsRequestDto(game.getId(), gameSeatIds);

        ReservationSeatsResponseDto resDto = reservationService.reserveSeat(member.getId(), reqDto);

        assertThat(resDto.seats()).hasSize(3);

        long seatPriceSum = resDto.seats().stream()
                .mapToLong(ReservationSeatsResponseDto.ReservationSeatDto::price)
                .sum();

        assertThat(resDto.amount()).isEqualTo(seatPriceSum);
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

        ArrayList<Long> memberIds = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            memberIds.add(fixture.createMemberWithNum(i).getId());
        }

        Random random = new Random();

        for (int i = 0; i < threadCount; i++) {
            requests.add(new ReservationSeatsRequestDto(game.getId(), gameSeatIds));
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
                    reservationService.reserveSeat(memberIds.get(random.nextInt(users)), req);
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

        ArrayList<Long> memberIds = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            memberIds.add(fixture.createMemberWithNum(i).getId());
        }

        for (int i = 0; i < threadCount; i++) {
            requests.add(new ReservationSeatsRequestDto(game.getId(), List.of(gameSeatIds.get(i))));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.reserveSeat(memberIds.get(finalI), requests.get(finalI));
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

        ArrayList<Long> memberIds = new ArrayList<>();
        ArrayList<ReservationSeatsRequestDto> requests = new ArrayList<>();

        for (int i = 0; i < users; i++) {
            memberIds.add(fixture.createMemberWithNum(i).getId());
        }

        Random random = new Random();

        for (int i = 0; i < threadCount / 2; i++) {
            requests.add(new ReservationSeatsRequestDto(game.getId(), List.of(gameSeatIds.get(i * 2), gameSeatIds.get(i * 2 + 1))));
            requests.add(new ReservationSeatsRequestDto(game.getId(), List.of(gameSeatIds.get(i * 2))));
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
                    reservationService.reserveSeat(memberIds.get(random.nextInt(users)), req);
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

}
