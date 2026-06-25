package com.sportsify.common.notification.payload;

public record NotificationEnvelope(
        Object data,
        NotificationFilter filter
) {
    public static NotificationEnvelope of(Object data, NotificationFilter filter) {
        return new NotificationEnvelope(data, filter);
    }

    public boolean isAlertEnabled() {
        return filter != null && filter.alertEnabled();
    }
}
