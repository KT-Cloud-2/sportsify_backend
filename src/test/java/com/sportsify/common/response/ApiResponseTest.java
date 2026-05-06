package com.sportsify.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    // ──────────────────────── ok(T data) ────────────────────────

    @Test
    @DisplayName("ok(data) — success=true, data 포함, error=null")
    void ok_withData_성공응답() {
        String payload = "hello";

        ApiResponse<String> response = ApiResponse.ok(payload);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ok(data) — timestamp가 현재 시각 근처로 설정된다")
    void ok_withData_타임스탬프설정() {
        Instant before = Instant.now();

        ApiResponse<String> response = ApiResponse.ok("test");

        Instant after = Instant.now();
        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("ok(data) — data가 null이어도 success=true")
    void ok_withNullData_성공응답() {
        ApiResponse<String> response = ApiResponse.ok((String) null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("ok(data) — 복잡한 객체도 래핑된다")
    void ok_withComplexObject_래핑성공() {
        ErrorDetail errorDetail = ErrorDetail.of("code", "message", "detail");

        ApiResponse<ErrorDetail> response = ApiResponse.ok(errorDetail);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(errorDetail);
    }

    // ──────────────────────── ok() ────────────────────────

    @Test
    @DisplayName("ok() — success=true, data=null, error=null")
    void ok_noArgs_성공응답() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ok() — timestamp가 현재 시각 근처로 설정된다")
    void ok_noArgs_타임스탬프설정() {
        Instant before = Instant.now();

        ApiResponse<Void> response = ApiResponse.ok();

        Instant after = Instant.now();
        assertThat(response.timestamp()).isBetween(before, after);
    }

    // ──────────────────────── error(ErrorDetail) ────────────────────────

    @Test
    @DisplayName("error(errorDetail) — success=false, data=null, error 포함")
    void error_에러응답() {
        ErrorDetail errorDetail = ErrorDetail.of("INVALID_INPUT", "입력값이 올바르지 않습니다.", null);

        ApiResponse<Void> response = ApiResponse.error(errorDetail);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isSameAs(errorDetail);
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("error(errorDetail) — timestamp가 현재 시각 근처로 설정된다")
    void error_타임스탬프설정() {
        ErrorDetail errorDetail = ErrorDetail.of("SOME_ERROR", "오류", "세부정보");
        Instant before = Instant.now();

        ApiResponse<Void> response = ApiResponse.error(errorDetail);

        Instant after = Instant.now();
        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("error(errorDetail) — detail이 있는 에러도 정상 래핑된다")
    void error_detail포함_에러응답() {
        ErrorDetail errorDetail = ErrorDetail.of("INVALID_INPUT", "입력값이 올바르지 않습니다.", "nickname: 2자 이상 입력해주세요.");

        ApiResponse<Void> response = ApiResponse.error(errorDetail);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("INVALID_INPUT");
        assertThat(response.error().message()).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(response.error().detail()).isEqualTo("nickname: 2자 이상 입력해주세요.");
    }

    // ──────────────────────── 상호 독립성 ────────────────────────

    @Test
    @DisplayName("ok() 와 error() 는 서로 다른 인스턴스를 반환한다")
    void ok_error_서로다른인스턴스() {
        ErrorDetail errorDetail = ErrorDetail.of("ERR", "에러", null);

        ApiResponse<Void> okResponse = ApiResponse.ok();
        ApiResponse<Void> errorResponse = ApiResponse.error(errorDetail);

        assertThat(okResponse).isNotSameAs(errorResponse);
        assertThat(okResponse.success()).isTrue();
        assertThat(errorResponse.success()).isFalse();
    }

    @Test
    @DisplayName("연속 호출 시 각 응답은 독립적인 timestamp를 가진다")
    void ok_연속호출_독립적타임스탬프() throws InterruptedException {
        ApiResponse<String> first = ApiResponse.ok("first");
        Thread.sleep(1);
        ApiResponse<String> second = ApiResponse.ok("second");

        assertThat(first.timestamp()).isBeforeOrEqualTo(second.timestamp());
    }
}