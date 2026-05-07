package com.sportsify.common.exception;

import com.sportsify.common.response.ErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.internalServerError()
                .body(ErrorDetail.of(ErrorCode.INTERNAL_ERROR.getCode(), e.getErrorCode().getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetail> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body(ErrorDetail.of(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), null));
    }
}
