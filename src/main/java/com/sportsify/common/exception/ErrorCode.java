package com.sportsify.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 리소스입니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", "비즈니스 규칙을 위반했습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청 한도를 초과했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    // 회원 / 인증
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "존재하지 않는 회원입니다."),
    NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "NICKNAME_DUPLICATE", "이미 사용 중인 닉네임입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 리프레시 토큰입니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "존재하지 않는 팀입니다."),
    FAVORITE_TEAM_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAVORITE_TEAM_ALREADY_EXISTS", "이미 등록된 선호 팀입니다."),
    FAVORITE_TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE_TEAM_NOT_FOUND", "선호 팀으로 등록되지 않은 팀입니다."),
    INVALID_PRIORITY(HttpStatus.BAD_REQUEST, "INVALID_PRIORITY", "우선순위 범위가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String toExampleJson(String detail) {
        String detailValue = (detail == null || detail.isBlank()) ? "null" : "\"" + detail + "\"";
        return """
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "%s",
                    "message": "%s",
                    "detail": %s
                  },
                  "timestamp": "2026-04-29T12:00:00Z"
                }""".formatted(code, message, detailValue);
    }
}
