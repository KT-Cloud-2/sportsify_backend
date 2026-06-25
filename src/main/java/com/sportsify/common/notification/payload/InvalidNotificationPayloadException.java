package com.sportsify.common.notification.payload;

public class InvalidNotificationPayloadException extends RuntimeException {

    public InvalidNotificationPayloadException(String message) {
        super(message);
    }
}
