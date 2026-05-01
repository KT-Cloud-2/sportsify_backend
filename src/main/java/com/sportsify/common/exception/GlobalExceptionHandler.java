package com.sportsify.common.exception;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.common.response.ErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorDetail errorDetail = ErrorDetail.of(errorCode.getCode(), errorCode.getMessage(), e.getDetail());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(CommonResponse.error(errorDetail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String detail = fieldError != null ? fieldError.getField() + ": " + fieldError.getDefaultMessage() : null;
        ErrorDetail errorDetail = ErrorDetail.of(
                ErrorCode.INVALID_INPUT.getCode(),
                ErrorCode.INVALID_INPUT.getMessage(),
                detail
        );
        return ResponseEntity.badRequest().body(CommonResponse.error(errorDetail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
        ErrorDetail errorDetail = ErrorDetail.of(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getMessage(),
                null
        );
        return ResponseEntity.internalServerError().body(CommonResponse.error(errorDetail));
    }
}
