package com.sportsify.common.response;

public record ErrorDetail(
        String code,
        String message,
        String detail
) {
    public static ErrorDetail of(String code, String message, String detail) {
        return new ErrorDetail(code, message, detail);
    }
}
