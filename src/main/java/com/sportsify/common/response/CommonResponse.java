package com.sportsify.common.response;

import java.time.Instant;

public record CommonResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        Instant timestamp
) {
    public static <T> CommonResponse<T> ok(T data) {
        return new CommonResponse<>(true, data, null, Instant.now());
    }

    public static CommonResponse<Void> ok() {
        return new CommonResponse<>(true, null, null, Instant.now());
    }

    public static CommonResponse<Void> error(ErrorDetail errorDetail) {
        return new CommonResponse<>(false, null, errorDetail, Instant.now());
    }
}
