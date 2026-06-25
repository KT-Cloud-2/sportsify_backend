package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.GameId;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] GameId VO 불변 조건 테스트")
class GameIdTest {

    @Test
    @DisplayName("양수 값으로 생성하면 값이 반환된다")
    void of_정상_생성() {
        GameId id = GameId.of(1L);

        assertThat(id.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null로 생성하면 null이 허용된다 (DIRECT 방은 gameId 없음)")
    void of_null_허용() {
        GameId id = GameId.of(null);

        assertThat(id.value()).isNull();
    }

    @Test
    @DisplayName("0이면 예외를 던진다")
    void of_0_예외() {
        assertThatThrownBy(() -> GameId.of(0L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("음수이면 예외를 던진다")
    void of_음수_예외() {
        assertThatThrownBy(() -> GameId.of(-1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 값이면 equals가 true를 반환한다")
    void equals_같은_값_true() {
        GameId a = GameId.of(1L);
        GameId b = GameId.of(1L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("null끼리는 equals가 true를 반환한다")
    void equals_null끼리_true() {
        GameId a = GameId.of(null);
        GameId b = GameId.of(null);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("다른 값이면 equals가 false를 반환한다")
    void equals_다른_값_false() {
        GameId a = GameId.of(1L);
        GameId b = GameId.of(2L);

        assertThat(a).isNotEqualTo(b);
    }
}
