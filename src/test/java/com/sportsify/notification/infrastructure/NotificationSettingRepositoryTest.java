package com.sportsify.notification.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;

    private static final PageRequest ALL = PageRequest.of(0, 1000);

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
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByTicketOpenAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).contains(memberId);
    }

    @Test
    @DisplayName("gameStartAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByGameStartAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByGameStartAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).contains(memberId);
    }

    @Test
    @DisplayName("paymentAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByPaymentAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByPaymentAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).contains(memberId);
    }

    @Test
    @DisplayName("ticketOpenAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByTicketOpenAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(false, true, true, true);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByTicketOpenAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).doesNotContain(memberId);
    }

    @Test
    @DisplayName("gameStartAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByGameStartAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(true, false, true, true);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByGameStartAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).doesNotContain(memberId);
    }

    @Test
    @DisplayName("paymentAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByPaymentAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(true, true, false, true);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByPaymentAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).doesNotContain(memberId);
    }

    @Test
    @DisplayName("chatMentionAlert가 true인 memberId 목록을 조회한다")
    void findMemberIdsByChatMentionAlertTrue_알림활성화된_멤버_조회() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByChatMentionAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).contains(memberId);
    }

    @Test
    @DisplayName("chatMentionAlert가 false이면 목록에 포함되지 않는다")
    void findMemberIdsByChatMentionAlertTrue_비활성화된_멤버_미포함() {
        // GIVEN
        NotificationSetting setting = NotificationSetting.createDefault(memberId);
        setting.update(true, true, true, false);
        notificationSettingRepository.save(setting);
        em.flush();

        // WHEN
        Slice<Long> memberIds = notificationSettingRepository.findMemberIdsByChatMentionAlertTrue(ALL);

        // THEN
        assertThat(memberIds.getContent()).doesNotContain(memberId);
    }
}
