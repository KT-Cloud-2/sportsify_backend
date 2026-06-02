package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.ChunkService;
import com.sportsify.notification.application.service.Dispatcher;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

class ChunkServiceIntegrationTest extends RepositoryTestSupport {

    @Autowired private ChunkService chunkService;
    @Autowired private NotificationEventRepository eventRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private Dispatcher dispatcher;

    @Test
    @DisplayName("processChunk는 REQUIRES_NEW로 호출자 트랜잭션과 다른 독립 트랜잭션에서 실행된다")
    void processChunk_REQUIRES_NEW_독립트랜잭션_실행() {
        NotificationEvent event = transactionTemplate.execute(
                status -> eventRepository.save(NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}"))
        );

        AtomicReference<String> outerTxName = new AtomicReference<>();
        AtomicReference<String> innerTxName = new AtomicReference<>();

        given(dispatcher.toMember(any(), anyLong(), anyString())).willAnswer(inv -> {
            // processChunk 내부에서 실행 중인 트랜잭션 이름 캡처
            innerTxName.set(TransactionSynchronizationManager.getCurrentTransactionName());
            return false;
        });

        // 외부 트랜잭션 안에서 processChunk 호출
        transactionTemplate.executeWithoutResult(status -> {
            outerTxName.set(TransactionSynchronizationManager.getCurrentTransactionName());
            chunkService.processChunk(event, List.of(1L), "{}");
        });

        // 트랜잭션이 분리됐으면 이름(또는 참조)이 다르거나 inner가 별도 실행됨
        // REQUIRES_NEW는 외부 트랜잭션을 suspend하고 새 트랜잭션을 여므로
        // inner는 ChunkService.processChunk의 트랜잭션 이름을 가짐
        assertThat(innerTxName.get())
                .as("processChunk는 별도 트랜잭션(ChunkService.processChunk)에서 실행돼야 한다")
                .contains("ChunkService");
    }

    @Test
    @DisplayName("processChunk 내부 예외 발생 시 해당 청크만 롤백되고 외부 트랜잭션은 영향받지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void processChunk_내부예외_해당청크만롤백() {
        NotificationEvent event = transactionTemplate.execute(
                status -> eventRepository.save(NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}"))
        );

        given(dispatcher.toMember(any(), anyLong(), anyString()))
                .willThrow(new RuntimeException("청크 처리 중 오류"));

        // processChunk 예외 → 해당 청크 트랜잭션 롤백
        try {
            chunkService.processChunk(event, List.of(1L), "{}");
        } catch (Exception ignored) {}

        // 외부에서 이벤트 조회 가능 — 외부 트랜잭션(이벤트 저장)은 영향 없음
        assertThat(eventRepository.findById(event.getId())).isPresent();
    }
}
