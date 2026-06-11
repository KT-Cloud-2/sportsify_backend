package com.sportsify.payment.application.service;

import com.sportsify.config.TestContainersConfig;
import org.springframework.context.annotation.Import;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.game.domain.model.Game;
import com.sportsify.member.domain.model.Member;
import com.sportsify.payment.application.dto.CreatePaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TicketingTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("결제 생성 성공 - 실제 Fixture 기반으로 연관관계를 맞추어 성공하는 시나리오")
    void createPayment_Integration_Success() {
        // given
        Member member = fixture.createMember("user@sportsify.com", "테스터닉네임");
        Game game = fixture.createGame();

        long ticketPrice = (long) fixture.TICKET_PRICE;
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 1);
        Long gameSeatId = gameSeatIds.get(0);

        Order order = fixture.createOrder(member, ticketPrice);
        fixture.createOrderSeat(order, gameSeatId);

        // 정상적으로 롬복 빌더 패턴 사용 가능
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(order.getId())
                .amount(ticketPrice)
                .matchId(game.getId())
                .seatId(gameSeatId)
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod("CARD")
                .build();

        // when
        PaymentResponse response = paymentService.createPayment(member.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(order.getId());
        assertThat(response.getAmount()).isEqualTo(ticketPrice);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("결제 생성 실패 - 주문 금액과 요청 결제 금액이 일치하지 않을 때")
    void createPayment_Integration_AmountMismatch_Fail() {
        // given
        Member member = fixture.createMember("user@sportsify.com", "테스터닉네임");
        Game game = fixture.createGame();
        long ticketPrice = (long) fixture.TICKET_PRICE;
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 1);
        Long gameSeatId = gameSeatIds.get(0);

        Order order = fixture.createOrder(member, ticketPrice);
        fixture.createOrderSeat(order, gameSeatId);

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(order.getId())
                .amount(ticketPrice + 5000L) // 금액 불일치 유도
                .matchId(game.getId())
                .seatId(gameSeatId)
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod("CARD")
                .build();

        // when & then
        assertThatThrownBy(() -> paymentService.createPayment(member.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("요청 금액이 주문 금액과 일치하지 않습니다.");
    }

    @Test
    @DisplayName("동시성 테스트 - 동일한 Idempotency Key로 다중 결제 요청이 동시에 올 때 모두 예외 없이 안전하게 완료되어야 함")
    void concurrency_multiplePaymentRequests_onlyOneShouldSuccess() throws InterruptedException {
        // given
        Member member = fixture.createMember("user@sportsify.com", "테스터닉네임");
        Game game = fixture.createGame();
        long ticketPrice = (long) fixture.TICKET_PRICE;
        List<Long> gameSeatIds = fixture.createGameSeatsWithCount(game, 1);
        Long gameSeatId = gameSeatIds.get(0);

        Order order = fixture.createOrder(member, ticketPrice);
        fixture.createOrderSeat(order, gameSeatId);

        String sharedIdempotencyKey = UUID.randomUUID().toString();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(order.getId())
                .amount(ticketPrice)
                .matchId(game.getId())
                .seatId(gameSeatId)
                .idempotencyKey(sharedIdempotencyKey)
                .paymentMethod("CARD")
                .build();

        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        List<PaymentResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // when
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    PaymentResponse response = paymentService.createPayment(member.getId(), request);
                    responses.add(response);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        assertThat(exceptions).isEmpty();
        assertThat(responses).hasSize(numberOfThreads);

        Long expectedPaymentId = responses.get(0).getPaymentId();
        for (PaymentResponse res : responses) {
            assertThat(res.getPaymentId()).isEqualTo(expectedPaymentId);
        }
    }
}
