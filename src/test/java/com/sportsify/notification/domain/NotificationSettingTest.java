package com.sportsify.notification.domain;

import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingTest {

    @Test
    @DisplayName("기본 설정은 모든 알림이 ON이다")
    void createDefault_모든알림ON() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        assertThat(setting.isEnabledFor(NotificationEventType.TICKET_OPEN)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.GAME_START)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.PAYMENT_COMPLETED)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.CHAT_MENTION)).isTrue();
    }

    @Test
    @DisplayName("TICKET_OPEN 알림을 OFF하면 isEnabledFor가 false를 반환한다")
    void update_티켓알림OFF() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        setting.update(false, true, true, true);

        assertThat(setting.isEnabledFor(NotificationEventType.TICKET_OPEN)).isFalse();
        assertThat(setting.isEnabledFor(NotificationEventType.GAME_START)).isTrue();
    }

    @Test
    @DisplayName("chatMentionAlert를 OFF하면 CHAT_MENTION isEnabledFor가 false를 반환한다")
    void update_채팅멘션알림OFF() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        setting.update(true, true, true, false);

        assertThat(setting.isEnabledFor(NotificationEventType.CHAT_MENTION)).isFalse();
        assertThat(setting.isEnabledFor(NotificationEventType.TICKET_OPEN)).isTrue();
    }
}
