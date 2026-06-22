package com.sportsify.common.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.sportsify.common.response.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;
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

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException e) {
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDetail> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ErrorDetail.of(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage(), e.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetail> handleException(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.warn("SSE 엔드포인트 예외 ({}): {}", request.getRequestURI(), e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return null;
        }
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
