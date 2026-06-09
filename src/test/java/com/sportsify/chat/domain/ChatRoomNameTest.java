package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomName;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] ChatRoomName VO 불변 조건 테스트")
class ChatRoomNameTest {

    @Test
    @DisplayName("유효한 이름으로 생성하면 값이 반환된다")
    void of_정상_생성() {
        ChatRoomName name = ChatRoomName.of("테스트 채팅방");

        assertThat(name.value()).isEqualTo("테스트 채팅방");
    }

    @Test
    @DisplayName("앞뒤 공백은 trim되어 저장된다")
    void of_앞뒤_공백_trim() {
        ChatRoomName name = ChatRoomName.of("  채팅방  ");

        assertThat(name.value()).isEqualTo("채팅방");
    }

    @Test
    @DisplayName("최대 길이(50자) 경계값은 생성에 성공한다")
    void of_최대길이_경계값_성공() {
        String maxLength = "a".repeat(ChatRoomName.MAX_LENGTH);

        ChatRoomName name = ChatRoomName.of(maxLength);

        assertThat(name.value()).hasSize(ChatRoomName.MAX_LENGTH);
    }

    @Test
    @DisplayName("trim 후 최대 길이를 초과하면 예외를 던진다")
    void of_trim_후_최대길이_초과_예외() {
        String tooLong = "a".repeat(ChatRoomName.MAX_LENGTH + 1);

        assertThatThrownBy(() -> ChatRoomName.of(tooLong))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("공백만 있는 문자열은 trim 후 blank가 되어 예외를 던진다")
    void of_공백만_있는_문자열_예외() {
        assertThatThrownBy(() -> ChatRoomName.of("   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("null이면 예외를 던진다")
    void of_null_예외() {
        assertThatThrownBy(() -> ChatRoomName.of(null))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("빈 문자열이면 예외를 던진다")
    void of_빈_문자열_예외(String blank) {
        assertThatThrownBy(() -> ChatRoomName.of(blank))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 값이면 equals가 true를 반환한다")
    void equals_같은_값_true() {
        ChatRoomName a = ChatRoomName.of("채팅방");
        ChatRoomName b = ChatRoomName.of("채팅방");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("trim 결과가 같으면 equals가 true를 반환한다")
    void equals_trim_결과_동일하면_true() {
        ChatRoomName a = ChatRoomName.of("채팅방");
        ChatRoomName b = ChatRoomName.of("  채팅방  ");

        assertThat(a).isEqualTo(b);
    }
}
