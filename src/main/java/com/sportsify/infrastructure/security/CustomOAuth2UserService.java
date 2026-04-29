package com.sportsify.infrastructure.security;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberJpaRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuth2UserInfo info = OAuth2UserInfo.of(registrationId, oAuth2User.getAttributes());

        Member member = memberRepository.findByProviderAndProviderId(info.provider(), info.providerId())
                .orElseGet(() -> memberRepository.save(
                        Member.create(info.email(), info.nickname(), info.provider(), info.providerId())
                ));

        member.updateLastLoginAt();

        return new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "id", member.getId(),
                        "email", member.getEmail() != null ? member.getEmail() : "",
                        "role", member.getRole().name()
                ),
                "id"
        );
    }
}
