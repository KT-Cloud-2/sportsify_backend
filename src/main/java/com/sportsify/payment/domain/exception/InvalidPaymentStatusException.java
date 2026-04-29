package com.sportsify.payment.domain.exception;

public class InvalidPaymentStatusException extends RuntimeException {
    public InvalidPaymentStatusException(String message) {
        super(message);
    }
}