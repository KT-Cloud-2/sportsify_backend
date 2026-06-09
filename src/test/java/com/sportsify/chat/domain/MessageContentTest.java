package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] MessageContent VO 불변 조건 테스트")
class MessageContentTest {

    @Test
    @DisplayName("유효한 내용으로 생성하면 값이 그대로 반환된다")
    void of_정상_생성() {
        MessageContent content = MessageContent.of("안녕하세요");

        assertThat(content.value()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("최대 길이(500자) 경계값은 생성에 성공한다")
    void of_최대길이_경계값_성공() {
        String maxLength = "a".repeat(MessageContent.MAX_LENGTH);

        MessageContent content = MessageContent.of(maxLength);

        assertThat(content.value()).hasSize(MessageContent.MAX_LENGTH);
    }

    @Test
    @DisplayName("최대 길이를 초과하면 예외를 던진다")
    void of_최대길이_초과_예외() {
        String tooLong = "a".repeat(MessageContent.MAX_LENGTH + 1);

        assertThatThrownBy(() -> MessageContent.of(tooLong))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("null이면 예외를 던진다")
    void of_null_예외() {
        assertThatThrownBy(() -> MessageContent.of(null))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("공백 문자열이면 예외를 던진다")
    void of_공백_예외(String blank) {
        assertThatThrownBy(() -> MessageContent.of(blank))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 값이면 equals가 true를 반환한다")
    void equals_같은_값_true() {
        MessageContent a = MessageContent.of("hello");
        MessageContent b = MessageContent.of("hello");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("다른 값이면 equals가 false를 반환한다")
    void equals_다른_값_false() {
        MessageContent a = MessageContent.of("hello");
        MessageContent b = MessageContent.of("world");

        assertThat(a).isNotEqualTo(b);
    }
}
