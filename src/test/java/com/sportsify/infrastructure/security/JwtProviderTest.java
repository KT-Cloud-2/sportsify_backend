package com.sportsify.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JwtProvider.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-at-least-32-chars-long",
        "jwt.access-token-expiry-ms=900000",
        "jwt.refresh-token-expiry-ms=1209600000"
})
class JwtProviderTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Nested
    @DisplayName("isValidIgnoringExpiry")
    class isValidIgnoringExpiry {

        @Test
        @DisplayName("유효한 토큰은 true를 반환한다")
        void 유효한_토큰_true() {
            // GIVEN
            String token = jwtProvider.createAccessToken(1L, "USER");

            // WHEN & THEN
            assertThat(jwtProvider.isValidIgnoringExpiry(token)).isTrue();
        }

        @Test
        @DisplayName("만료된 토큰도 서명이 유효하면 true를 반환한다")
        void 만료된_토큰_서명유효_true() {
            // GIVEN: 이미 만료된 토큰 (expiry=0ms)
            String expiredToken = jwtProvider.createAccessTokenWithExpiry(1L, "USER", 0L);

            // WHEN & THEN
            assertThat(jwtProvider.isValidIgnoringExpiry(expiredToken)).isTrue();
        }

        @Test
        @DisplayName("빈 문자열 토큰은 false를 반환한다")
        void 빈문자열_토큰_false() {
            assertThat(jwtProvider.isValidIgnoringExpiry("")).isFalse();
        }

        @Test
        @DisplayName("형식이 잘못된 토큰은 false를 반환한다")
        void 잘못된형식_토큰_false() {
            assertThat(jwtProvider.isValidIgnoringExpiry("invalid.token.value")).isFalse();
        }
    }

    @Nested
    @DisplayName("getMemberIdIgnoringExpiry")
    class getMemberIdIgnoringExpiry {

        @Test
        @DisplayName("만료된 토큰에서도 memberId를 추출한다")
        void 만료된_토큰_memberId_추출() {
            // GIVEN
            String expiredToken = jwtProvider.createAccessTokenWithExpiry(42L, "USER", 0L);

            // WHEN
            Long memberId = jwtProvider.getMemberIdIgnoringExpiry(expiredToken);

            // THEN
            assertThat(memberId).isEqualTo(42L);
        }
    }
}
