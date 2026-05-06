package com.sportsify.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorCodeTest {

    // ──────────────────────── 공통 에러 코드 ────────────────────────

    @Test
    @DisplayName("INVALID_INPUT — 400 상태, 올바른 code와 message")
    void invalidInput_필드검증() {
        ErrorCode code = ErrorCode.INVALID_INPUT;

        assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(code.getMessage()).isEqualTo("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("UNAUTHORIZED — 401 상태")
    void unauthorized_상태코드검증() {
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("FORBIDDEN — 403 상태")
    void forbidden_상태코드검증() {
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("NOT_FOUND — 404 상태")
    void notFound_상태코드검증() {
        assertThat(ErrorCode.NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("CONFLICT — 409 상태")
    void conflict_상태코드검증() {
        assertThat(ErrorCode.CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("INTERNAL_ERROR — 500 상태")
    void internalError_상태코드검증() {
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(ErrorCode.INTERNAL_ERROR.getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    // ──────────────────────── 도메인 에러 코드 ────────────────────────

    @Test
    @DisplayName("MEMBER_NOT_FOUND — 404 상태")
    void memberNotFound_상태코드검증() {
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getCode()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getMessage()).isEqualTo("존재하지 않는 회원입니다.");
    }

    @Test
    @DisplayName("NICKNAME_DUPLICATE — 409 상태")
    void nicknameDuplicate_상태코드검증() {
        assertThat(ErrorCode.NICKNAME_DUPLICATE.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.NICKNAME_DUPLICATE.getCode()).isEqualTo("NICKNAME_DUPLICATE");
    }

    @Test
    @DisplayName("INVALID_REFRESH_TOKEN — 401 상태")
    void invalidRefreshToken_상태코드검증() {
        assertThat(ErrorCode.INVALID_REFRESH_TOKEN.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.INVALID_REFRESH_TOKEN.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("TEAM_NOT_FOUND — 404 상태")
    void teamNotFound_상태코드검증() {
        assertThat(ErrorCode.TEAM_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("FAVORITE_TEAM_ALREADY_EXISTS — 409 상태")
    void favoriteTeamAlreadyExists_상태코드검증() {
        assertThat(ErrorCode.FAVORITE_TEAM_ALREADY_EXISTS.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("FAVORITE_TEAM_NOT_FOUND — 404 상태")
    void favoriteTeamNotFound_상태코드검증() {
        assertThat(ErrorCode.FAVORITE_TEAM_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("INVALID_PRIORITY — 400 상태")
    void invalidPriority_상태코드검증() {
        assertThat(ErrorCode.INVALID_PRIORITY.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_PRIORITY.getCode()).isEqualTo("INVALID_PRIORITY");
    }

    // ──────────────────────── toExampleJson 메서드 제거 확인 ────────────────────────

    @Test
    @DisplayName("ErrorCode에 toExampleJson 메서드가 존재하지 않는다 (제거됨)")
    void toExampleJson_메서드없음() {
        assertThatThrownBy(() -> {
            ErrorCode.class.getMethod("toExampleJson", String.class);
        }).isInstanceOf(NoSuchMethodException.class);
    }

    // ──────────────────────── 전체 enum 값 ────────────────────────

    @Test
    @DisplayName("모든 ErrorCode 값은 non-null httpStatus, code, message를 가진다")
    void allValues_필수필드_비어있지않음() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getHttpStatus())
                    .as("ErrorCode.%s 의 httpStatus가 null", errorCode.name())
                    .isNotNull();
            assertThat(errorCode.getCode())
                    .as("ErrorCode.%s 의 code가 null 또는 빈 값", errorCode.name())
                    .isNotBlank();
            assertThat(errorCode.getMessage())
                    .as("ErrorCode.%s 의 message가 null 또는 빈 값", errorCode.name())
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("모든 ErrorCode의 code 문자열이 enum 이름과 일치한다")
    void allValues_코드문자열이enum이름과일치() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode())
                    .as("ErrorCode.%s 의 code가 enum 이름과 불일치", errorCode.name())
                    .isEqualTo(errorCode.name());
        }
    }

    @Test
    @DisplayName("TOO_MANY_REQUESTS — 429 상태")
    void tooManyRequests_상태코드검증() {
        assertThat(ErrorCode.TOO_MANY_REQUESTS.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.getCode()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    @DisplayName("BUSINESS_RULE_VIOLATION — 422 상태")
    void businessRuleViolation_상태코드검증() {
        assertThat(ErrorCode.BUSINESS_RULE_VIOLATION.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}