package com.sportsify.common.notification.payload;

public record NotificationFilter(boolean alertEnabled) {

    public static NotificationFilter enabled() {
        return new NotificationFilter(true);
    }

    public static NotificationFilter disabled() {
        return new NotificationFilter(false);
    }
}
