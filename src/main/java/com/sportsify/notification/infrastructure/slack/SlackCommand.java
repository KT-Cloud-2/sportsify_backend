package com.sportsify.notification.infrastructure.slack;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

//  사용법: /server notify event streamKey=payment.completed id=1234567890-0
@Getter
public enum SlackCommand {

    NOTIFY_EVENT("notify event", "notify event streamKey={streamKey} id={id}");

    private final String keyword;
    private final String usage;

    SlackCommand(String keyword, String usage) {
        this.keyword = keyword;
        this.usage = usage;
    }

    public static Optional<SlackCommand> resolve(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        return Arrays.stream(values())
                .sorted((a, b) -> b.keyword.length() - a.keyword.length())
                .filter(cmd -> trimmed.startsWith(cmd.keyword))
                .findFirst();
    }
}
