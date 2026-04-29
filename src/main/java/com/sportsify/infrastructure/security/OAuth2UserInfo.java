package com.sportsify.infrastructure.security;

import com.sportsify.member.domain.model.OAuthProvider;

import java.util.Map;

public record OAuth2UserInfo(OAuthProvider provider, String providerId, String email, String nickname) {

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> new OAuth2UserInfo(
                    OAuthProvider.GOOGLE,
                    (String) attributes.get("sub"),
                    (String) attributes.get("email"),
                    (String) attributes.get("name")
            );
            case "kakao" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                yield new OAuth2UserInfo(
                        OAuthProvider.KAKAO,
                        String.valueOf(attributes.get("id")),
                        (String) kakaoAccount.get("email"),
                        (String) profile.get("nickname")
                );
            }
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth2 provider: " + registrationId);
        };
    }
}
