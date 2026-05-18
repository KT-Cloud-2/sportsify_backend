package com.sportsify.infrastructure.security;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberJpaRepository memberRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuth2UserInfo info = OAuth2UserInfo.of(registrationId, oAuth2User.getAttributes());

        Optional<Member> existing = memberRepository.findByProviderAndProviderId(info.provider(), info.providerId());

        Member member;
        if (existing.isEmpty()) {
            member = memberRepository.save(
                    Member.create(info.email(), info.nickname(), info.provider(), info.providerId())
            );
            notificationSettingRepository.save(NotificationSetting.createDefault(member.getId()));
        } else {
            member = existing.get();
        }

        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }

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
