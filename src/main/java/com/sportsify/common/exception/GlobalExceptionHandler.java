package com.sportsify.common.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.sportsify.common.response.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorDetail> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorDetail.of(errorCode.getCode(), errorCode.getMessage(), e.getDetail()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetail> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String detail = fieldError != null ? fieldError.getField() + ": " + fieldError.getDefaultMessage() : null;
        return ResponseEntity.badRequest()
                .body(ErrorDetail.of(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage(), detail));
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ErrorDetail> handleInfrastructureException(InfrastructureException e) {
        log.error("Infrastructure failure errorCode={} message={}", e.getErrorCode(), e.getErrorCode().getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(ErrorDetail.of(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetail> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(ErrorDetail.of(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(ErrorDetail.of(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage(), detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();

        if (cause instanceof JsonParseException || cause instanceof JsonMappingException) {
            return ResponseEntity.badRequest()
                    .body(ErrorDetail.of(
                            ErrorCode.REQUEST_BODY_MALFORMED.getCode(),
                            ErrorCode.REQUEST_BODY_MALFORMED.getMessage(),
                            cause.getMessage()
                    ));
        }
        return ResponseEntity.badRequest()
                .body(ErrorDetail.of(
                        ErrorCode.REQUEST_INVALID.getCode(),
                        ErrorCode.REQUEST_INVALID.getMessage(),
                        null
                ));
    }
}
