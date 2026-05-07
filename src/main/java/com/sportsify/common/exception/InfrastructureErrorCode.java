package com.sportsify.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InfrastructureErrorCode {

    REDIS_STREAMS_INIT_FAILED("Redis Streams consumer group 초기화에 실패했습니다."),
    MQTT_CONNECTION_FAILED("MQTT 브로커 연결에 실패했습니다.");

    private final String message;
}
