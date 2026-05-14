package com.sportsify.common.notification;

import com.sportsify.common.notification.payload.NotificationPayload;

/**
 * 알림 이벤트 발행 인터페이스.
 *
 * <p>eventType과 payload는 반드시 아래 쌍으로 사용해야 합니다.
 * 잘못된 조합은 런타임 파싱 실패로 이어집니다.
 *
 * <pre>
 * TICKET_OPEN       → TicketOpenPayload   (saleStartAt 있으면 예약 발송, 없으면 즉시)
 * GAME_START        → GameStartPayload    (gameStartAt 30분 전 예약 발송, 없으면 즉시)
 * PAYMENT_COMPLETED → PaymentCompletedPayload  (즉시 발송)
 * CHAT_MENTION      → ChatMentionPayload        (즉시 발송)
 * </pre>
 */
public interface NotificationEventPublisher {

    void publish(NotificationEventType eventType, NotificationPayload payload);
}
