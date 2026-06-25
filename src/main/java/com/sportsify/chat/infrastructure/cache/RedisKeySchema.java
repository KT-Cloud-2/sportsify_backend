package com.sportsify.chat.infrastructure.cache;

public final class RedisKeySchema {

    public static final String SCAN_PATTERN = "chat:read:*";
    public static final String LAST_READ_KEY_PREFIX = "chat:read:%d:%d";
    public static final long LAST_READ_TTL_SECONDS = 60 * 60 * 24L;
    public static final String UNREAD_COUNT_PATTERN = "chat:unread:count:%d";

    public static final String ROOM_NOTIFY_KEY_PREFIX = "chat:room:notify:%d";
    public static final long ROOM_NOTIFY_TTL_SECONDS = 60 * 60 * 24L;
    public static final String ROOM_NOTIFY_LOCK_KEY_PREFIX = "chat:room:lock:%d";
    public static final long ROOM_NOTIFY_LOCK_TTL_MS = 5000L;
}
