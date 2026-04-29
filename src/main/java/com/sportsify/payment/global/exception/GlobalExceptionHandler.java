package com.sportsify.payment.global.exception;

import com.sportsify.payment.domain.exception.InvalidPaymentAmountException;
import com.sportsify.payment.domain.exception.InvalidPaymentStatusException;
import com.sportsify.payment.domain.exception.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        e.printStackTrace(); // 개발 중에는 콘솔에 찍기
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", e.getMessage() == null ? "알 수 없는 예외" : e.getMessage(),
                        "exception", e.getClass().getName()
                ));
    }
}