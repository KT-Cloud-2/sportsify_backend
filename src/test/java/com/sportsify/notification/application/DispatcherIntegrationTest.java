package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.application.service.Dispatcher;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventStatus;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.infrastructure.sse.SseEmitterManager;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

class DispatcherIntegrationTest extends NotificationIntegrationTestSupport {

    @Autowired private Dispatcher dispatcher;
    @Autowired private EventStatusService statusService;
    @Autowired private NotificationEventRepository eventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationChannelRepository channelRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private SseEmitterManager sseEmitterManager;

    private Long memberId;

    @BeforeEach
    void setUp() {
        willDoNothing().given(sseEmitterManager).send(anyLong(), anyString());

        String uid = UUID.randomUUID().toString().substring(0, 8);
        memberId = transactionTemplate.execute(status -> {
            Member member = memberJpaRepository.save(
                    Member.create(uid + "@test.com", "tester", OAuthProvider.GOOGLE, uid));
            return member.getId();
        });
    }

    @Test
    @DisplayName("fanout 실패로 FAILED 마킹 시 NotificationEvent 상태가 FAILED로 저장된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fanout실패시_이벤트상태_FAILED() {
        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        transactionTemplate.execute(status -> {
            statusService.markEventStatus(event.getId(), true);
            return null;
        });

        NotificationEvent saved = transactionTemplate.execute(status ->
                eventRepository.findById(event.getId()).orElseThrow()
        );

        assertThat(saved.getStatus()).isEqualTo(NotificationEventStatus.FAILED);
    }

    @Test
    @DisplayName("이미 발송된 이벤트를 PEL 재처리해도 Notification이 중복 생성되지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void PUBLISHED_이벤트_PEL재처리_중복발송없음() {
        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        transactionTemplate.execute(status -> {
            dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
            return null;
        });

        assertThat(notificationCountFor(memberId, event.getId())).isEqualTo(1);

        transactionTemplate.execute(status -> {
            boolean result = dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
            assertThat(result).isFalse();
            return null;
        });

        assertThat(notificationCountFor(memberId, event.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("알림 발송 시 Notification이 read=false 상태로 DB에 저장된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 알림발송시_Notification_unread_저장() {
        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        transactionTemplate.execute(status -> {
            dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
            return null;
        });

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT is_read FROM notifications WHERE member_id = ? AND event_id = ?",
                memberId, event.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("is_read")).isEqualTo(false);
    }

    @Test
    @DisplayName("발송 완료 후 NotificationEvent가 PUBLISHED로 마킹되어 정리 대상이 된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 발송완료후_NotificationEvent_PUBLISHED_마킹() {
        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        transactionTemplate.execute(status -> {
            dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
            return null;
        });
        transactionTemplate.execute(status -> {
            statusService.markEventStatus(event.getId(), false);
            return null;
        });

        NotificationEvent saved = transactionTemplate.execute(status ->
                eventRepository.findById(event.getId()).orElseThrow()
        );

        assertThat(saved.getStatus()).isEqualTo(NotificationEventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SSE 연결이 없어도 Notification은 read=false로 DB에 저장된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void SSE_연결없어도_Notification_unread_저장() {
        willThrow(new RuntimeException("SSE 연결 없음")).given(sseEmitterManager).send(anyLong(), anyString());

        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        try {
            transactionTemplate.execute(status -> {
                dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
                return null;
            });
        } catch (RuntimeException ignored) {
            // afterCommit에서 SSE 예외가 전파됨 — 트랜잭션은 이미 커밋
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT is_read FROM notifications WHERE member_id = ? AND event_id = ?",
                memberId, event.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("is_read")).isEqualTo(false);
    }

    @Test
    @DisplayName("EMAIL 채널 발송 실패 시 NotificationHistory에 이력이 기록된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 채널발송실패시_NotificationHistory_이력기록() {
        transactionTemplate.execute(status -> {
            channelRepository.save(
                    NotificationChannel.create(memberId, NotificationChannelType.EMAIL, "invalid-target@fail.com"));
            return null;
        });

        NotificationEvent event = transactionTemplate.execute(status ->
                eventRepository.save(NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED,
                        "{\"memberId\":" + memberId + "}"))
        );

        transactionTemplate.execute(status -> {
            dispatcher.toMember(event, memberId, "{\"memberId\":" + memberId + "}");
            return null;
        });

        assertThat(notificationCountFor(memberId, event.getId())).isEqualTo(1);

        List<Map<String, Object>> historyRows = jdbcTemplate.queryForList(
                "SELECT nh.status FROM notification_history nh " +
                "JOIN notifications n ON nh.notification_id = n.id " +
                "WHERE n.member_id = ? AND n.event_id = ?",
                memberId, event.getId());

        assertThat(historyRows).isNotEmpty();
    }

    private long notificationCountFor(Long mId, Long eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = ? AND event_id = ?",
                Long.class, mId, eventId);
        return count != null ? count : 0L;
    }
}
