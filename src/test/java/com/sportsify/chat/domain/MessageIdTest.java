package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] MessageId VO 불변 조건 테스트")
class MessageIdTest {

    @Test
    @DisplayName("양수 값으로 생성하면 값이 반환된다")
    void of_정상_생성() {
        MessageId id = MessageId.of(1L);

        assertThat(id.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null이면 예외를 던진다")
    void of_null_예외() {
        assertThatThrownBy(() -> MessageId.of(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("0이면 예외를 던진다")
    void of_0_예외() {
        assertThatThrownBy(() -> MessageId.of(0L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("음수이면 예외를 던진다")
    void of_음수_예외() {
        assertThatThrownBy(() -> MessageId.of(-1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 값이면 equals가 true를 반환한다")
    void equals_같은_값_true() {
        MessageId a = MessageId.of(1L);
        MessageId b = MessageId.of(1L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("다른 값이면 equals가 false를 반환한다")
    void equals_다른_값_false() {
        MessageId a = MessageId.of(1L);
        MessageId b = MessageId.of(2L);

        assertThat(a).isNotEqualTo(b);
    }
}
