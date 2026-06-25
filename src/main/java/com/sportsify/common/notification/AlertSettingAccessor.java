package com.sportsify.common.notification;

public interface AlertSettingAccessor {
    boolean isTicketOpenAlert();
    boolean isGameStartAlert();
    boolean isPaymentAlert();
    boolean isChatMentionAlert();
}
