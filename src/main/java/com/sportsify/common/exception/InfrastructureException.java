package com.sportsify.common.exception;

/**
 * 인프라 초기화/연결 실패처럼 비즈니스 로직과 무관한 시스템 레벨 예외.
 * GlobalExceptionHandler가 500으로 처리한다.
 */
public class InfrastructureException extends RuntimeException {

    private final InfrastructureErrorCode errorCode;

    public InfrastructureException(InfrastructureErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public InfrastructureErrorCode getErrorCode() {
        return errorCode;
    }
}
