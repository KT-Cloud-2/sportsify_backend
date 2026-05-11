package com.sportsify.chat.application.message.config;

public final class RedisKeySchema {
    public static final String SCAN_PATTERN = "chat:read:*";
    public static final String LAST_READ_KEY_PREFIX = "chat:read:%d:%d";
    public static final long LAST_READ_TTL_SECONDS = 60 * 60 * 24L;
}
