package com.sportsify.ticketing.infrastructure;


import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.ticketing.domain.model.Order;
import com.sportsify.ticketing.domain.model.OrderSeat;
import com.sportsify.ticketing.domain.repository.OrderRepository;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private TicketingTestFixture fixture;

    @BeforeEach
    void beforeEach() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("주문과 관련된 게임ID를 반환한다.")
    void findGameIdByOrderId() {
        Game game = fixture.createGame();
        Order order = Order.create(fixture.createMember("t1@test.com", "n1"));
        GameSeat gameSeat = gameSeatRepository
                .findById(fixture.createGameSeatsWithCount(game, 1).getFirst())
                .orElseThrow();
        order.addOrderSeat(OrderSeat.create(order, gameSeat, gameSeat.getPrice()));
        order.calculateTotalAmount();
        orderRepository.save(order);

        Long gameIdByOrderId = orderRepository.findGameIdByOrderId(order.getId());
        assertThat(gameIdByOrderId).isEqualTo(game.getId());
    }
}
