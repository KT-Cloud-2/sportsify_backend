package com.sportsify.notification.infrastructure.repository;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.NotificationSettingPort;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSettingPortAdapter implements NotificationSettingPort {

    private final NotificationSettingRepository settingRepository;

    @Override
    public boolean isAlertEnabled(Long memberId, NotificationEventType eventType) {
        return settingRepository.findByMemberId(memberId)
                .map(s -> s.isEnabledFor(eventType))
                .orElse(true);
    }
}
