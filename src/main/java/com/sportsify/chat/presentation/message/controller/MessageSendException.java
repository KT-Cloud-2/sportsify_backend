package com.sportsify.chat.presentation.message.controller;

public class MessageSendException extends RuntimeException {

    private final String clientMessageId;

    public MessageSendException(Exception cause, String clientMessageId) {
        super(cause);
        this.clientMessageId = clientMessageId;
    }

    public String clientMessageId() {
        return clientMessageId;
    }
}
