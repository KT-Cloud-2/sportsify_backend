package com.sportsify.notification.application;

import com.sportsify.notification.infrastructure.sse.SseEmitterManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterManagerTest {

    private final SseEmitterManager manager = new SseEmitterManager();

    @Test
    @DisplayName("subscribe 후 isConnected가 true를 반환한다")
    void subscribe_연결상태true() {
        manager.subscribe(1L);
        assertThat(manager.isConnected(1L)).isTrue();
    }

    @Test
    @DisplayName("미연결 사용자에게 send를 호출해도 예외가 발생하지 않는다")
    void send_미연결사용자_예외없음() {
        manager.send(999L, "data");
    }

    @Test
    @DisplayName("unsubscribe 후 isConnected가 false가 된다")
    void completion_연결해제() {
        manager.subscribe(1L);
        manager.unsubscribe(1L);
        assertThat(manager.isConnected(1L)).isFalse();
    }
}
