package com.sportsify.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        DefaultOAuth2User oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
        Long memberId = ((Number) oAuth2User.getAttributes().get("id")).longValue();
        String role = (String) oAuth2User.getAttributes().get("role");

        String accessToken = jwtProvider.createAccessToken(memberId, role);
        String refreshToken = jwtProvider.createRefreshToken(memberId);

        redisTemplate.opsForValue().set(
                "auth:refresh:" + memberId,
                refreshToken,
                jwtProvider.getRefreshTokenExpiryMs(),
                TimeUnit.MILLISECONDS
        );

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
