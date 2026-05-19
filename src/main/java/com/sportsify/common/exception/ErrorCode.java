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
    MEMBER_WITHDRAWN(HttpStatus.FORBIDDEN, "MEMBER_WITHDRAWN", "탈퇴한 회원입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 리프레시 토큰입니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "존재하지 않는 팀입니다."),
    FAVORITE_TEAM_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAVORITE_TEAM_ALREADY_EXISTS", "이미 등록된 선호 팀입니다."),
    FAVORITE_TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE_TEAM_NOT_FOUND", "선호 팀으로 등록되지 않은 팀입니다."),
    INVALID_PRIORITY(HttpStatus.BAD_REQUEST, "INVALID_PRIORITY", "우선순위 범위가 올바르지 않습니다."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ALREADY_READ(HttpStatus.BAD_REQUEST, "NOTIFICATION_ALREADY_READ", "이미 읽은 알림입니다."),
    NOTIFICATION_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_SETTING_NOT_FOUND", "알림 설정을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_CHANNEL_NOT_FOUND", "알림 채널을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_ALREADY_EXISTS(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_ALREADY_EXISTS", "이미 등록된 알림 채널입니다."),
    NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED", "지원하지 않는 알림 채널 타입입니다."),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_SEND_FAILED", "알림 발송에 실패했습니다."),

    // 예매
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND", "존재하지 않는 경기입니다."),
    GAME_NOT_ON_SALE(HttpStatus.valueOf(422), "GAME_NOT_ON_SALE", "판매 중이 아닌 경기입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND", "존재하지 않는 좌석입니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "SEAT_ALREADY_RESERVED", "이미 선점된 좌석입니다."),
    SEAT_IS_NULL(HttpStatus.NOT_FOUND, "SEAT_IS_NULL", "선택된 좌석이 없습니다."),
    SEAT_DUPLICATED(HttpStatus.BAD_REQUEST, "SEAT_DUPLICATED", "중복된 좌석이 있습니다."),
    TICKET_LIMIT_EXCEEDED(HttpStatus.valueOf(422), "TICKET_LIMIT_EXCEEDED", "경기당 1인 최대 4매를 초과했습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "존재하지 않는 주문입니다."),
    ORDER_EXPIRED(HttpStatus.GONE, "ORDER_EXPIRED", "예약 시간이 만료되었습니다."),

    // 게임
    PRICE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "PRICE_POLICY_NOT_FOUND", "가격 정책이 존재하지 않습니다."),
    STADIUM_NOT_FOUND(HttpStatus.NOT_FOUND, "STADIUM_NOT_FOUND", "존재하지 않는 경기장입니다.");


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
                    "code": "%s",
                    "message": "%s",
                    "detail": %s
                }""".formatted(code, message, detailValue);
    }
}
