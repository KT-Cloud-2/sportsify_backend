package com.sportsify.notification.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationChannelRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationChannelRepository notificationChannelRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(
            Member.create("channel@test.com", "채널테스터", OAuthProvider.GOOGLE, "google-channel-001")
        );
        memberId = member.getId();
    }

    @Test
    @DisplayName("동일한 memberId + channelType으로 채널을 두 번 등록하면 DataIntegrityViolationException이 발생한다")
    void save_중복채널_예외() {
        notificationChannelRepository.save(
            NotificationChannel.create(memberId, NotificationChannelType.EMAIL, "a@test.com")
        );
        em.flush();

        assertThatThrownBy(() -> {
            notificationChannelRepository.save(
                NotificationChannel.create(memberId, NotificationChannelType.EMAIL, "b@test.com")
            );
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("isEnabled=true인 채널만 조회된다")
    void findByMemberIdAndEnabledTrue_활성채널만() {
        // GIVEN
        notificationChannelRepository.save(
            NotificationChannel.create(memberId, NotificationChannelType.EMAIL, "a@test.com")
        );
        NotificationChannel mqtt = notificationChannelRepository.save(
            NotificationChannel.create(memberId, NotificationChannelType.MQTT, "client-1")
        );
        mqtt.toggle();
        notificationChannelRepository.save(mqtt);

        // WHEN
        var channels = notificationChannelRepository.findByMemberIdAndEnabledTrue(memberId);

        // THEN
        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).getChannelType()).isEqualTo(NotificationChannelType.EMAIL);
    }

    @Test
    @DisplayName("memberId와 channelType으로 채널 존재 여부를 확인한다")
    void existsByMemberIdAndChannelType_존재하면_true() {
        // GIVEN
        notificationChannelRepository.save(
            NotificationChannel.create(memberId, NotificationChannelType.EMAIL, "a@test.com")
        );
        em.flush();

        // WHEN
        boolean exists = notificationChannelRepository.existsByMemberIdAndChannelType(memberId, NotificationChannelType.EMAIL);
        boolean notExists = notificationChannelRepository.existsByMemberIdAndChannelType(memberId, NotificationChannelType.MQTT);

        // THEN
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}
