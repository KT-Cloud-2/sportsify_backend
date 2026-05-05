package com.sportsify.notification.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(
            Member.create("test@test.com", "테스트유저", OAuthProvider.GOOGLE, "google-test-001")
        );
        memberId = member.getId();
    }

    @Test
    @DisplayName("동일한 eventId + memberId로 알림을 두 번 저장하면 DataIntegrityViolationException이 발생한다")
    void save_중복알림_예외() {
        NotificationEvent event = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        notificationRepository.save(Notification.create(memberId, event.getId()));
        em.flush();

        assertThatThrownBy(() -> {
            notificationRepository.save(Notification.create(memberId, event.getId()));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("memberId로 알림 목록을 최신순으로 페이징 조회한다")
    void findByMemberIdOrderByCreatedAtDesc_페이징() {
        NotificationEvent event1 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}")
        );
        NotificationEvent event2 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        notificationRepository.save(Notification.create(memberId, event1.getId()));
        notificationRepository.save(Notification.create(memberId, event2.getId()));

        Page<Notification> page = notificationRepository
            .findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
