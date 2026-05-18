package com.sportsify.game.domain;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.Seat;
import com.sportsify.game.domain.model.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class GameSeatTest {
    @Test
    @DisplayName("release 호출 시 게임 좌석 상태가 AVAILABLE로 변경된다.")
    void changeStatusWhenRelease() {
        Game mockGame = mock(Game.class);
        Seat mockSeat = mock(Seat.class);
        GameSeat gameSeat = GameSeat.builder().game(mockGame).seat(mockSeat).price(10000).build();

        gameSeat.updateSeatStatus(SeatStatus.RESERVED);

        assertThat(gameSeat.getSeatStatus()).isEqualTo(SeatStatus.RESERVED);

        gameSeat.release();

        assertThat(gameSeat.getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);

    }
}
