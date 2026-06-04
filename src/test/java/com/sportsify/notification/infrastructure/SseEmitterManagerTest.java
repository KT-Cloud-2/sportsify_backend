package com.sportsify.notification.infrastructure;

import com.sportsify.notification.infrastructure.sse.SseEmitterManager;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class SseEmitterManagerTest {

    private final SseEmitterManager manager = new SseEmitterManager(NotificationIntegrationTestSupport.defaultProperties());

    // ─── 연결 (subscribe) ────────────────────────────────────────────────

    @Nested
    @DisplayName("SSE 연결")
    class 연결 {

        @Test
        @DisplayName("subscribe 후 isConnected가 true를 반환한다")
        void subscribe_연결상태true() {
            // GIVEN & WHEN
            manager.subscribe(1L);

            // THEN
            assertThat(manager.isConnected(1L)).isTrue();
        }

        @Test
        @DisplayName("동일 멤버가 재구독하면 기존 emitter를 complete 처리하고 교체된다")
        void subscribe_중복구독_기존emitter교체() {
            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                // GIVEN
                manager.subscribe(1L);
                SseEmitter first = mocked.constructed().get(0);

                // WHEN
                manager.subscribe(1L);

                // THEN: 기존 emitter가 complete 되었어야 한다
                verify(first).complete();
                assertThat(manager.isConnected(1L)).isTrue();
            }
        }
    }

    // ─── 연결 끊김 (disconnect) ───────────────────────────────────────────

    @Nested
    @DisplayName("SSE 연결 끊김")
    class 연결끊김 {

        @Test
        @DisplayName("onCompletion 콜백이 호출되면 emitter가 제거된다")
        void onCompletion_emitter제거() {
            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                // GIVEN
                manager.subscribe(1L);

                // WHEN: onCompletion에 등록된 람다를 직접 실행
                ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
                verify(mocked.constructed().get(0)).onCompletion(captor.capture());
                captor.getValue().run();

                // THEN
                assertThat(manager.isConnected(1L)).isFalse();
            }
        }

        @Test
        @DisplayName("onTimeout 콜백이 호출되면 emitter가 제거된다")
        void onTimeout_emitter제거() {
            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                // GIVEN
                manager.subscribe(1L);

                // WHEN: 타임아웃 발생 시뮬레이션
                ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
                verify(mocked.constructed().get(0)).onTimeout(captor.capture());
                captor.getValue().run();

                // THEN
                assertThat(manager.isConnected(1L)).isFalse();
            }
        }

        @Test
        @DisplayName("onError 콜백이 호출되면 emitter가 제거된다")
        void onError_emitter제거() {
            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                // GIVEN
                manager.subscribe(1L);

                // WHEN: 클라이언트 연결 오류 시뮬레이션
                ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
                verify(mocked.constructed().get(0)).onError(captor.capture());
                captor.getValue().accept(new IOException("클라이언트 연결 끊김"));

                // THEN
                assertThat(manager.isConnected(1L)).isFalse();
            }
        }

        @Test
        @DisplayName("send 중 IOException 발생 시 emitter가 제거된다")
        void send_IOException_emitter제거() throws IOException {
            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class,
                    (mock, ctx) -> doThrow(new IOException("broken pipe"))
                            .when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {

                // GIVEN
                manager.subscribe(1L);

                // WHEN: send 중 네트워크 오류 발생
                manager.send(1L, "data");

                // THEN
                assertThat(manager.isConnected(1L)).isFalse();
            }
        }

        @Test
        @DisplayName("unsubscribe 호출 시 emitter가 제거된다")
        void unsubscribe_emitter제거() {
            // GIVEN
            manager.subscribe(1L);

            // WHEN
            manager.unsubscribe(1L);

            // THEN
            assertThat(manager.isConnected(1L)).isFalse();
        }
    }

    // ─── send ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SSE 메시지 전송")
    class 메시지전송 {

        @Test
        @DisplayName("미연결 사용자에게 send를 호출해도 예외가 발생하지 않는다")
        void send_미연결사용자_예외없음() {
            // GIVEN & WHEN & THEN
            manager.send(999L, "data");
        }
    }
}
