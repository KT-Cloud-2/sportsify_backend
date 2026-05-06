package com.sportsify.notification.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.infrastructure.repository.NotificationSettingRepositoryAdapter;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationSettingRepositoryAdapter notificationSettingRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(
            Member.create("setting@test.com", "설정테스터", OAuthProvider.GOOGLE, "google-setting-001")
        );
        memberId = member.getId();
    }

    @Test
    @DisplayName("ticketOpenAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByTicketOpenAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByTicketOpenAlertTrue();

        // THEN
        assertThat(memberIds).contains(memberId);
    }

    @Test
    @DisplayName("gameStartAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByGameStartAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByGameStartAlertTrue();

        // THEN
        assertThat(memberIds).contains(memberId);
    }

    @Test
    @DisplayName("paymentAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByPaymentAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByPaymentAlertTrue();

        // THEN
        assertThat(memberIds).contains(memberId);
    }

    @Test
    @DisplayName("ticketOpenAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByTicketOpenAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(false, true, true);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByTicketOpenAlertTrue();

        // THEN
        assertThat(memberIds).doesNotContain(memberId);
    }

    @Test
    @DisplayName("gameStartAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByGameStartAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(true, false, true);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByGameStartAlertTrue();

        // THEN
        assertThat(memberIds).doesNotContain(memberId);
    }

    @Test
    @DisplayName("paymentAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByPaymentAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(true, true, false);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        List<Long> memberIds = notificationSettingRepository.findMemberIdsByPaymentAlertTrue();

        // THEN
        assertThat(memberIds).doesNotContain(memberId);
    }
}
