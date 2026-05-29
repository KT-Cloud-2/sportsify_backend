package com.sportsify.notification.infrastructure;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventStatus;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import com.sportsify.notification.infrastructure.consumer.PelMessageProcessor;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import com.sportsify.notification.infrastructure.sse.SseEmitterManager;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
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

class PelPermanentFailureIntegrationTest extends NotificationIntegrationTestSupport {

    @Autowired private PelMessageProcessor pelMessageProcessor;
    @Autowired private NotificationEventRepository eventRepository;
    @Autowired private NotificationChannelRepository channelRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private NotificationProperties properties;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private StringRedisTemplate redisTemplate;

    @MockitoBean private SseEmitterManager sseEmitterManager;

    private Long memberId;

    @BeforeEach
    void setUp() {
        willDoNothing().given(sseEmitterManager).send(anyLong(), anyString());

        String uid = UUID.randomUUID().toString().substring(0, 8);
        memberId = transactionTemplate.execute(status -> {
            Member member = memberJpaRepository.save(
                    Member.create(uid + "@pel.com", "peltester", OAuthProvider.GOOGLE, uid));
            channelRepository.save(
                    NotificationChannel.create(member.getId(), NotificationChannelType.EMAIL, uid + "@pel.com"));
            return member.getId();
        });

        String streamKey = NotificationEventType.PAYMENT_COMPLETED.getStreamKey();
        try {
            redisTemplate.opsForStream().createGroup(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("채널 없는 회원도 영구 실패 시 Notification 기록이 unread로 저장된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void backoff_마지막_재발송_실패시_채널없는회원_Notification_unread_저장() {
        // 채널이 없어도 Dispatcher.toMember는 Notification을 저장하지만 fanout이 실패를 반환하지 않아 PUBLISHED로 끝남
        // 영구 실패 경로에서는 saveNotificationForPermanentlyFailed가 별도로 Notification을 저장해야 한다
        int backoffSize = properties.pel().backoffMinutes().size();
        String streamKey = NotificationEventType.PAYMENT_COMPLETED.getStreamKey();
        String msgId = "100-0";

        Long noChannelMemberId = transactionTemplate.execute(status -> {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            Member m = memberJpaRepository.save(
                    Member.create(uid + "@nochannel.com", "nochannel", OAuthProvider.GOOGLE, uid));
            return m.getId();
        });

        String payload = "{\"memberId\":" + noChannelMemberId + "}";

        NotificationEvent event = transactionTemplate.execute(status -> {
            NotificationEvent e = NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, payload);
            for (int i = 0; i < backoffSize - 1; i++) {
                e.incrementRetry();
            }
            e.assignStreamMessageId(msgId);
            return eventRepository.save(e);
        });

        MapRecord<String, Object, Object> message = mapRecord(streamKey, payload, msgId);
        pelMessageProcessor.process(streamKey, NotificationEventType.PAYMENT_COMPLETED, message);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT is_read FROM notifications WHERE member_id = ? AND event_id = ?",
                noChannelMemberId, event.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("is_read")).isEqualTo(false);
    }

    @Test
    @DisplayName("saveNotificationForPermanentlyFailed 예외 시에도 이벤트는 PERMANENTLY_FAILED 상태를 유지한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 영구실패_알림저장_예외시_이벤트_상태는_PERMANENTLY_FAILED_유지() {
        // 존재하지 않는 memberId로 payload를 구성 → payloadParser는 정상 파싱하지만
        // Notification.create 후 save 시 member FK 위반 등 예외가 발생해도
        // 이벤트 상태는 PERMANENTLY_FAILED로 유지되어야 한다
        int backoffSize = properties.pel().backoffMinutes().size();
        String streamKey = NotificationEventType.PAYMENT_COMPLETED.getStreamKey();
        String msgId = "200-0";
        long nonExistentMemberId = -9999L;
        String payload = "{\"memberId\":" + nonExistentMemberId + "}";

        NotificationEvent event = transactionTemplate.execute(status -> {
            NotificationEvent e = NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, payload);
            for (int i = 0; i < backoffSize - 1; i++) {
                e.incrementRetry();
            }
            e.assignStreamMessageId(msgId);
            return eventRepository.save(e);
        });

        MapRecord<String, Object, Object> message = mapRecord(streamKey, payload, msgId);
        pelMessageProcessor.process(streamKey, NotificationEventType.PAYMENT_COMPLETED, message);

        NotificationEventStatus finalStatus = transactionTemplate.execute(status ->
                eventRepository.findById(event.getId()).orElseThrow().getStatus());

        assertThat(finalStatus).isEqualTo(NotificationEventStatus.PERMANENTLY_FAILED);
    }

    @Test
    @DisplayName("PEL 재시도 소진 후 추가 process 호출이 와도 fanout이 실행되지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void PEL_소진후_추가재처리_fanout없음() {
        int backoffSize = properties.pel().backoffMinutes().size();
        String payload = "{\"memberId\":" + memberId + "}";
        String streamKey = NotificationEventType.PAYMENT_COMPLETED.getStreamKey();
        String msgId = "300-0";

        NotificationEvent event = transactionTemplate.execute(status -> {
            NotificationEvent e = NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, payload);
            for (int i = 0; i < backoffSize; i++) {
                e.incrementRetry();
            }
            e.markPermanentlyFailed();
            e.assignStreamMessageId(msgId);
            return eventRepository.save(e);
        });

        MapRecord<String, Object, Object> message = mapRecord(streamKey, payload, msgId);

        pelMessageProcessor.process(streamKey, NotificationEventType.PAYMENT_COMPLETED, message);

        long notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = ? AND event_id = ?",
                Long.class, memberId, event.getId());

        assertThat(notificationCount).isEqualTo(0);

        NotificationEventStatus finalStatus = transactionTemplate.execute(status ->
                eventRepository.findById(event.getId()).orElseThrow().getStatus());
        assertThat(finalStatus).isEqualTo(NotificationEventStatus.PERMANENTLY_FAILED);
    }

    private MapRecord<String, Object, Object> mapRecord(String streamKey, String payload, String msgId) {
        return MapRecord.create(streamKey,
                Map.<Object, Object>of(RedisStreamNotificationEventPublisher.PAYLOAD_KEY, payload))
                .withId(RecordId.of(msgId));
    }
}
