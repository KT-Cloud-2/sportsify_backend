package com.sportsify.ticketing.domain.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderConstants {
    public static final int EXPIRATION_MINUTES = 10;
    public static final int CLEANUP_DELAY_MINUTES = EXPIRATION_MINUTES + 2;
}
