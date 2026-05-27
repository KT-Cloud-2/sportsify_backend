package com.sportsify.common.notification.payload;

public record ChatMentionPayload(
        Long memberId,
        Long roomId,
        String roomName,
        Long senderId,
        String message
) implements NotificationPayload {

    private static final int PREVIEW_MAX_LENGTH = 15;

    public static ChatMentionPayload ofText(Long memberId, Long roomId, String roomName, Long senderId, String message) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, toPreview(message));
    }

    public static ChatMentionPayload ofImage(Long memberId, Long roomId, String roomName, Long senderId) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, "(이미지 포함)");
    }

    public static ChatMentionPayload ofFile(Long memberId, Long roomId, String roomName, Long senderId) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, "(파일 포함)");
    }

    private static String toPreview(String message) {
        if (message == null || message.isBlank()) return "";
        return message.length() > PREVIEW_MAX_LENGTH ? message.substring(0, PREVIEW_MAX_LENGTH) + "..." : message;
    }
}
