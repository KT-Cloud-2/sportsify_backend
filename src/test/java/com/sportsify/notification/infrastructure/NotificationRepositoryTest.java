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

    @Test
    @DisplayName("id와 memberId로 알림을 조회한다 — 일치하면 반환, 다른 memberId면 empty")
    void findByIdAndMemberId_존재하면_반환() {
        // GIVEN
        NotificationEvent event = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}")
        );
        Notification saved = notificationRepository.save(Notification.create(memberId, event.getId()));
        em.flush();

        // WHEN
        var found = notificationRepository.findByIdAndMemberId(saved.getId(), memberId);
        var notFound = notificationRepository.findByIdAndMemberId(saved.getId(), memberId + 999L);

        // THEN
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("eventId와 memberId로 알림 존재 여부를 확인한다 — 존재하면 true, 없으면 false")
    void existsByEventIdAndMemberId_존재하면_true() {
        // GIVEN
        NotificationEvent event = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        notificationRepository.save(Notification.create(memberId, event.getId()));
        em.flush();

        // WHEN
        boolean exists = notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId);
        boolean notExists = notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId + 999L);

        // THEN
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("memberId의 모든 읽지 않은 알림을 읽음 처리한다")
    void markAllReadByMemberId_읽음처리() {
        // GIVEN
        NotificationEvent event1 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}")
        );
        NotificationEvent event2 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        Notification notification1 = notificationRepository.save(Notification.create(memberId, event1.getId()));
        Notification notification2 = notificationRepository.save(Notification.create(memberId, event2.getId()));
        em.flush();

        // WHEN
        notificationRepository.markAllReadByMemberId(memberId);
        em.flush();
        em.clear();

        // THEN
        var result1 = notificationRepository.findByIdAndMemberId(notification1.getId(), memberId);
        var result2 = notificationRepository.findByIdAndMemberId(notification2.getId(), memberId);
        assertThat(result1).isPresent();
        assertThat(result1.get().isAlreadyRead()).isTrue();
        assertThat(result2).isPresent();
        assertThat(result2.get().isAlreadyRead()).isTrue();
    }
}
