package com.sportsify.payment.global.exception;

import com.sportsify.payment.domain.exception.InvalidPaymentAmountException;
import com.sportsify.payment.domain.exception.InvalidPaymentStatusException;
import com.sportsify.payment.domain.exception.PaymentNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<?> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentAmountException.class)
    public ResponseEntity<?> handleInvalidAmount(InvalidPaymentAmountException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentStatusException.class)
    public ResponseEntity<?> handleInvalidStatus(InvalidPaymentStatusException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }


}