package com.sportsify.common.notification;

import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public enum NotificationSettingKey {
    TICKET(0, AlertSettingAccessor::isTicketOpenAlert),
    GAME(1, AlertSettingAccessor::isGameStartAlert),
    PAYMENT(2, AlertSettingAccessor::isPaymentAlert),
    CHAT(3, AlertSettingAccessor::isChatMentionAlert);

    private final int index;
    private final Function<AlertSettingAccessor, Boolean> enabledChecker;

    public int getIndex() {
        return index;
    }

    public boolean isEnabledIn(AlertSettingAccessor accessor) {
        return enabledChecker.apply(accessor);
    }
}
