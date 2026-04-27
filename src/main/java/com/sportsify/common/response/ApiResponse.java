package com.sportsify.common.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(ErrorDetail errorDetail) {
        return new ApiResponse<>(false, null, errorDetail, Instant.now());
    }
}
