package com.sportsify.notification.domain;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationChannelTest {

    @Test
    @DisplayName("소유자가 맞으면 validateOwner가 예외를 던지지 않는다")
    void validateOwner_소유자일치_정상통과() {
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@b.com");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> channel.validateOwner(1L));
    }

    @Test
    @DisplayName("소유자가 다르면 BusinessException을 던진다")
    void validateOwner_소유자불일치_예외발생() {
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@b.com");

        assertThatThrownBy(() -> channel.validateOwner(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("toggle은 활성 채널을 비활성으로 전환한다")
    void toggle_활성채널_비활성전환() {
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@b.com");
        assertThat(channel.isEnabled()).isTrue();

        channel.toggle();

        assertThat(channel.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toggle을 두 번 호출하면 다시 활성 상태가 된다")
    void toggle_두번호출_활성복원() {
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@b.com");

        channel.toggle();
        channel.toggle();

        assertThat(channel.isEnabled()).isTrue();
    }
}
