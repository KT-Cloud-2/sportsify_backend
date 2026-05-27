package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.NotificationStreamQueryPort;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import com.sportsify.notification.infrastructure.slack.SlackCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackCommandService {

    private final NotificationStreamQueryPort streamQueryPort;
    private final RedisStreamNotificationEventPublisher streamPublisher;

    public String handle(String text) {
        Optional<SlackCommand> command = SlackCommand.resolve(text);
        return command.map(slackCommand -> switch (slackCommand) {
            case NOTIFY_EVENT -> handleNotifyEvent(text);
        }).orElseGet(() -> "알 수 없는 명령어입니다.\n사용 가능한 명령어:\n" + buildUsageGuide());
    }

    private String handleNotifyEvent(String text) {
        Map<String, String> params = parseParams(text);
        String streamKey = params.get("streamKey");
        String id = params.get("id");

        if (streamKey == null || id == null) {
            return "사용법: " + SlackCommand.NOTIFY_EVENT.getUsage();
        }

        Optional<NotificationEventType> eventTypeOpt = NotificationEventType.fromStreamKey(streamKey);
        if (eventTypeOpt.isEmpty()) {
            return "유효하지 않은 streamKey: " + streamKey;
        }
        NotificationEventType eventType = eventTypeOpt.get();

        Optional<String> payload = streamQueryPort.findPayload(eventType, id);
        if (payload.isEmpty()) {
            return "해당 메시지를 찾을 수 없습니다. streamKey=" + streamKey + " id=" + id;
        }

        streamPublisher.republish(eventType, payload.get());
        log.info("Slack 명령어로 알림 재발행 streamKey={} id={}", streamKey, id);
        return "재발송 완료 streamKey=" + streamKey + " id=" + id;
    }

    private Map<String, String> parseParams(String text) {
        Map<String, String> params = new HashMap<>();
        for (String token : text.split("\\s+")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private String buildUsageGuide() {
        StringBuilder sb = new StringBuilder();
        for (SlackCommand cmd : SlackCommand.values()) {
            sb.append("• /server ").append(cmd.getUsage()).append("\n");
        }
        return sb.toString();
    }
}
