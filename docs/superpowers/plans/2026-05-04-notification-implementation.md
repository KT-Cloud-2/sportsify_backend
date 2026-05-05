# 알림 도메인 구현 계획서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redis Streams 기반 알림 이벤트를 EMAIL/MQTT로 발송하고 SSE 실시간 인박스를 제공하는 `notification` 도메인 전체를 구현한다.

**Architecture:** Strategy 패턴(`NotificationSender` interface)으로 채널을 추상화하고, Consumer Group ACK + Outbox 패턴으로 at-least-once 신뢰성을 확보한다. `notifications.UNIQUE(event_id, member_id)`가 중복 방지 최종 보장, Redis 키는 보조 수단이다.

**Tech Stack:** Spring Boot 4.0.3, Java 25, Spring Data JPA, Spring Data Redis (Streams), Spring Mail, Spring Integration MQTT, Spring Web SSE (`SseEmitter`), PostgreSQL 18, Flyway, JUnit 5, Mockito, Testcontainers

---

## 파일 맵

### 새로 생성

```
src/main/resources/db/migration/
  V2__add_notification_tables.sql

src/main/java/com/sportsify/notification/
  domain/model/
    NotificationEventType.java
    NotificationEventStatus.java
    NotificationChannelType.java
    NotificationSendStatus.java
    NotificationEvent.java
    Notification.java
    NotificationSetting.java
    NotificationChannel.java
    NotificationHistory.java
  domain/repository/
    NotificationEventRepository.java
    NotificationRepository.java
    NotificationSettingRepository.java
    NotificationChannelRepository.java
    NotificationHistoryRepository.java
  application/dto/
    NotificationResult.java
    NotificationSettingResult.java
    NotificationChannelResult.java
  application/sender/
    NotificationSender.java
    EmailNotificationSender.java
    MqttNotificationSender.java
  application/sse/
    SseEmitterManager.java
  application/service/
    NotificationEventProcessor.java
    NotificationService.java
    NotificationSettingService.java
  application/consumer/
    NotificationStreamConsumer.java
  infrastructure/repository/
    NotificationEventJpaRepository.java
    NotificationEventRepositoryAdapter.java
    NotificationJpaRepository.java
    NotificationRepositoryAdapter.java
    NotificationSettingJpaRepository.java
    NotificationSettingRepositoryAdapter.java
    NotificationChannelJpaRepository.java
    NotificationChannelRepositoryAdapter.java
    NotificationHistoryJpaRepository.java
    NotificationHistoryRepositoryAdapter.java
  infrastructure/config/
    MailConfig.java
    MqttConfig.java
    RedisStreamsConfig.java
  presentation/api/
    NotificationApi.java
    NotificationSettingApi.java
  presentation/controller/
    NotificationController.java
    NotificationSettingController.java
  presentation/dto/
    NotificationResponse.java
    NotificationSettingResponse.java
    UpdateNotificationSettingRequest.java
    NotificationChannelResponse.java
    RegisterChannelRequest.java

src/test/java/com/sportsify/notification/
  domain/
    NotificationSettingTest.java
  application/
    NotificationEventProcessorTest.java
    NotificationServiceTest.java
    NotificationSettingServiceTest.java
    EmailNotificationSenderTest.java
    MqttNotificationSenderTest.java
    SseEmitterManagerTest.java
  infrastructure/
    NotificationRepositoryTest.java
    NotificationChannelRepositoryTest.java
  presentation/
    NotificationControllerApiTest.java
    NotificationSettingControllerApiTest.java
```

### 수정

```
src/main/java/com/sportsify/common/exception/ErrorCode.java  ← 알림 오류 코드 추가
src/main/java/com/sportsify/infrastructure/config/SecurityConfig.java  ← /api/v1/notifications/stream 허용
src/main/resources/application.yml  ← mail, mqtt 설정 추가
build.gradle  ← spring-boot-starter-mail, spring-integration-mqtt 주석 해제
```

---

## Task 1: Flyway 마이그레이션 + ErrorCode 추가

**Files:**
- Create: `src/main/resources/db/migration/V2__add_notification_tables.sql`
- Modify: `src/main/java/com/sportsify/common/exception/ErrorCode.java`

- [ ] **Step 1: V2 마이그레이션 파일 작성**

```sql
-- src/main/resources/db/migration/V2__add_notification_tables.sql

CREATE TABLE notification_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    payload      JSONB,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP    NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_ne_status ON notification_events(status, created_at);

CREATE TABLE notification_settings (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT    NOT NULL,
    ticket_open_alert   BOOLEAN   NOT NULL DEFAULT TRUE,
    game_start_alert    BOOLEAN   NOT NULL DEFAULT TRUE,
    payment_alert       BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMP,
    CONSTRAINT fk_ns_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_ns_member UNIQUE (member_id)
);

CREATE TABLE notification_channels (
    id              BIGSERIAL    PRIMARY KEY,
    member_id       BIGINT       NOT NULL,
    channel_type    VARCHAR(20)  NOT NULL,
    channel_target  VARCHAR(500) NOT NULL,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_nc_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT uq_nc        UNIQUE (member_id, channel_type)
);

CREATE INDEX idx_nc_member ON notification_channels(member_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT    NOT NULL,
    event_id   BIGINT    NOT NULL,
    is_read    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_noti_event  FOREIGN KEY (event_id)  REFERENCES notification_events(id),
    CONSTRAINT uq_noti        UNIQUE (event_id, member_id)
);

CREATE INDEX idx_noti_member ON notifications(member_id, is_read, created_at DESC);

CREATE TABLE notification_history (
    id               BIGSERIAL   PRIMARY KEY,
    notification_id  BIGINT      NOT NULL,
    channel_type     VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    error_message    TEXT,
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_nh_notification FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

CREATE INDEX idx_nh_notification ON notification_history(notification_id);
```

- [ ] **Step 2: ErrorCode에 알림 도메인 오류 코드 추가**

`src/main/java/com/sportsify/common/exception/ErrorCode.java` 파일의 마지막 enum 상수 뒤에 추가:

```java
    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ALREADY_READ(HttpStatus.BAD_REQUEST, "NOTIFICATION_ALREADY_READ", "이미 읽은 알림입니다."),
    NOTIFICATION_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_SETTING_NOT_FOUND", "알림 설정을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_CHANNEL_NOT_FOUND", "알림 채널을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_ALREADY_EXISTS(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_ALREADY_EXISTS", "이미 등록된 알림 채널입니다."),
    NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED", "지원하지 않는 알림 채널 타입입니다."),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_SEND_FAILED", "알림 발송에 실패했습니다.");
```

- [ ] **Step 3: build.gradle 의존성 주석 해제**

`build.gradle`에서 주석 처리된 두 줄을 활성화:

```groovy
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.integration:spring-integration-mqtt'
```

- [ ] **Step 4: 앱 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/db/migration/V2__add_notification_tables.sql \
        src/main/java/com/sportsify/common/exception/ErrorCode.java \
        build.gradle
git commit -m "feat(notification): add DB migration, error codes, mail/mqtt dependencies"
```

---

## Task 2: 도메인 모델 (Enum + Entity)

**Files:**
- Create: `src/main/java/com/sportsify/notification/domain/model/` 하위 9개 파일

- [ ] **Step 1: Enum 4종 작성**

```java
// NotificationEventType.java
package com.sportsify.notification.domain.model;

public enum NotificationEventType {
    TICKET_OPEN, GAME_START, PAYMENT_COMPLETED, CHAT_MENTION
}
```

```java
// NotificationEventStatus.java
package com.sportsify.notification.domain.model;

public enum NotificationEventStatus {
    PENDING, PUBLISHED, FAILED
}
```

```java
// NotificationChannelType.java
package com.sportsify.notification.domain.model;

public enum NotificationChannelType {
    EMAIL, MQTT, SLACK
}
```

```java
// NotificationSendStatus.java
package com.sportsify.notification.domain.model;

public enum NotificationSendStatus {
    SENT, FAILED
}
```

- [ ] **Step 2: NotificationEvent 엔티티 작성**

```java
// NotificationEvent.java
package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static NotificationEvent create(NotificationEventType eventType, String payload) {
        NotificationEvent event = new NotificationEvent();
        event.eventType = eventType;
        event.payload = payload;
        event.status = NotificationEventStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void markPublished() {
        this.status = NotificationEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationEventStatus.FAILED;
        this.publishedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Notification 엔티티 작성**

```java
// Notification.java
package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Notification create(Long memberId, Long eventId) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.eventId = eventId;
        notification.read = false;
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public void markRead() {
        this.read = true;
    }

    public boolean isAlreadyRead() {
        return this.read;
    }
}
```

- [ ] **Step 4: NotificationSetting 엔티티 작성**

```java
// NotificationSetting.java
package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "ticket_open_alert", nullable = false)
    private boolean ticketOpenAlert;

    @Column(name = "game_start_alert", nullable = false)
    private boolean gameStartAlert;

    @Column(name = "payment_alert", nullable = false)
    private boolean paymentAlert;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationSetting createDefault(Long memberId) {
        NotificationSetting setting = new NotificationSetting();
        setting.memberId = memberId;
        setting.ticketOpenAlert = true;
        setting.gameStartAlert = true;
        setting.paymentAlert = true;
        return setting;
    }

    public void update(boolean ticketOpenAlert, boolean gameStartAlert, boolean paymentAlert) {
        this.ticketOpenAlert = ticketOpenAlert;
        this.gameStartAlert = gameStartAlert;
        this.paymentAlert = paymentAlert;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEnabledFor(NotificationEventType eventType) {
        return switch (eventType) {
            case TICKET_OPEN -> ticketOpenAlert;
            case GAME_START -> gameStartAlert;
            case PAYMENT_COMPLETED -> paymentAlert;
            case CHAT_MENTION -> true;
        };
    }
}
```

- [ ] **Step 5: NotificationChannel 엔티티 작성**

```java
// NotificationChannel.java
package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_channels",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "channel_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private NotificationChannelType channelType;

    @Column(name = "channel_target", nullable = false)
    private String channelTarget;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationChannel create(Long memberId, NotificationChannelType channelType, String channelTarget) {
        NotificationChannel channel = new NotificationChannel();
        channel.memberId = memberId;
        channel.channelType = channelType;
        channel.channelTarget = channelTarget;
        channel.enabled = true;
        channel.createdAt = LocalDateTime.now();
        return channel;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 6: NotificationHistory 엔티티 작성**

```java
// NotificationHistory.java
package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private NotificationChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSendStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static NotificationHistory sent(Long notificationId, NotificationChannelType channelType) {
        NotificationHistory history = new NotificationHistory();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = NotificationSendStatus.SENT;
        history.createdAt = LocalDateTime.now();
        return history;
    }

    public static NotificationHistory failed(Long notificationId, NotificationChannelType channelType, String errorMessage) {
        NotificationHistory history = new NotificationHistory();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = NotificationSendStatus.FAILED;
        history.errorMessage = errorMessage;
        history.createdAt = LocalDateTime.now();
        return history;
    }
}
```

- [ ] **Step 7: 단위 테스트 작성 (NotificationSetting)**

```java
// src/test/java/com/sportsify/notification/domain/NotificationSettingTest.java
package com.sportsify.notification.domain;

import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingTest {

    @Test
    @DisplayName("기본 설정은 모든 알림이 ON이다")
    void createDefault_모든알림ON() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        assertThat(setting.isEnabledFor(NotificationEventType.TICKET_OPEN)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.GAME_START)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.PAYMENT_COMPLETED)).isTrue();
        assertThat(setting.isEnabledFor(NotificationEventType.CHAT_MENTION)).isTrue();
    }

    @Test
    @DisplayName("TICKET_OPEN 알림을 OFF하면 isEnabledFor가 false를 반환한다")
    void update_티켓알림OFF() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        setting.update(false, true, true);

        assertThat(setting.isEnabledFor(NotificationEventType.TICKET_OPEN)).isFalse();
        assertThat(setting.isEnabledFor(NotificationEventType.GAME_START)).isTrue();
    }

    @Test
    @DisplayName("CHAT_MENTION은 설정과 무관하게 항상 true이다")
    void isEnabledFor_채팅멘션_항상true() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);
        setting.update(false, false, false);

        assertThat(setting.isEnabledFor(NotificationEventType.CHAT_MENTION)).isTrue();
    }
}
```

- [ ] **Step 8: 테스트 실행**

```bash
./gradlew test --tests "com.sportsify.notification.domain.NotificationSettingTest" -i
```

Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/sportsify/notification/domain/ \
        src/test/java/com/sportsify/notification/domain/
git commit -m "feat(notification): add domain models and enums"
```

---

## Task 3: Repository 인터페이스 + Infrastructure 구현체

**Files:**
- Create: `src/main/java/com/sportsify/notification/domain/repository/` 5개
- Create: `src/main/java/com/sportsify/notification/infrastructure/repository/` 10개

- [ ] **Step 1: Repository 인터페이스 5종 작성**

```java
// NotificationEventRepository.java
package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import java.util.Optional;

public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent event);
    Optional<NotificationEvent> findById(Long id);
}
```

```java
// NotificationRepository.java
package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);
    void markAllReadByMemberId(Long memberId);
}
```

```java
// NotificationSettingRepository.java
package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository {
    NotificationSetting save(NotificationSetting setting);
    Optional<NotificationSetting> findByMemberId(Long memberId);
    List<Long> findMemberIdsByTicketOpenAlertTrue();
    List<Long> findMemberIdsByGameStartAlertTrue();
    List<Long> findMemberIdsByPaymentAlertTrue();
}
```

```java
// NotificationChannelRepository.java
package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository {
    NotificationChannel save(NotificationChannel channel);
    Optional<NotificationChannel> findById(Long id);
    Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId);
    boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    void delete(NotificationChannel channel);
}
```

```java
// NotificationHistoryRepository.java
package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationHistory;

public interface NotificationHistoryRepository {
    NotificationHistory save(NotificationHistory history);
}
```

- [ ] **Step 2: JPA Repository + Adapter 10종 작성**

```java
// NotificationEventJpaRepository.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {
}
```

```java
// NotificationEventRepositoryAdapter.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {
    private final NotificationEventJpaRepository jpaRepository;

    @Override
    public NotificationEvent save(NotificationEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<NotificationEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
```

```java
// NotificationJpaRepository.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.memberId = :memberId AND n.read = false")
    void markAllReadByMemberId(Long memberId);
}
```

```java
// NotificationRepositoryAdapter.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {
    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findByIdAndMemberId(Long id, Long memberId) {
        return jpaRepository.findByIdAndMemberId(id, memberId);
    }

    @Override
    public Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable) {
        return jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    @Override
    public boolean existsByEventIdAndMemberId(Long eventId, Long memberId) {
        return jpaRepository.existsByEventIdAndMemberId(eventId, memberId);
    }

    @Override
    public void markAllReadByMemberId(Long memberId) {
        jpaRepository.markAllReadByMemberId(memberId);
    }
}
```

```java
// NotificationSettingJpaRepository.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByMemberId(Long memberId);

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.ticketOpenAlert = true")
    List<Long> findMemberIdsByTicketOpenAlertTrue();

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.gameStartAlert = true")
    List<Long> findMemberIdsByGameStartAlertTrue();

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.paymentAlert = true")
    List<Long> findMemberIdsByPaymentAlertTrue();
}
```

```java
// NotificationSettingRepositoryAdapter.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationSettingRepositoryAdapter implements NotificationSettingRepository {
    private final NotificationSettingJpaRepository jpaRepository;

    @Override
    public NotificationSetting save(NotificationSetting setting) {
        return jpaRepository.save(setting);
    }

    @Override
    public Optional<NotificationSetting> findByMemberId(Long memberId) {
        return jpaRepository.findByMemberId(memberId);
    }

    @Override
    public List<Long> findMemberIdsByTicketOpenAlertTrue() {
        return jpaRepository.findMemberIdsByTicketOpenAlertTrue();
    }

    @Override
    public List<Long> findMemberIdsByGameStartAlertTrue() {
        return jpaRepository.findMemberIdsByGameStartAlertTrue();
    }

    @Override
    public List<Long> findMemberIdsByPaymentAlertTrue() {
        return jpaRepository.findMemberIdsByPaymentAlertTrue();
    }
}
```

```java
// NotificationChannelJpaRepository.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannel, Long> {
    Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId);
    boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
}
```

```java
// NotificationChannelRepositoryAdapter.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationChannelRepositoryAdapter implements NotificationChannelRepository {
    private final NotificationChannelJpaRepository jpaRepository;

    @Override
    public NotificationChannel save(NotificationChannel channel) {
        return jpaRepository.save(channel);
    }

    @Override
    public Optional<NotificationChannel> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType) {
        return jpaRepository.findByMemberIdAndChannelType(memberId, channelType);
    }

    @Override
    public List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId) {
        return jpaRepository.findByMemberIdAndEnabledTrue(memberId);
    }

    @Override
    public boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType) {
        return jpaRepository.existsByMemberIdAndChannelType(memberId, channelType);
    }

    @Override
    public void delete(NotificationChannel channel) {
        jpaRepository.delete(channel);
    }
}
```

```java
// NotificationHistoryJpaRepository.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryJpaRepository extends JpaRepository<NotificationHistory, Long> {
}
```

```java
// NotificationHistoryRepositoryAdapter.java
package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryRepositoryAdapter implements NotificationHistoryRepository {
    private final NotificationHistoryJpaRepository jpaRepository;

    @Override
    public NotificationHistory save(NotificationHistory history) {
        return jpaRepository.save(history);
    }
}
```

- [ ] **Step 3: 통합 테스트 작성 (Repository)**

```java
// src/test/java/com/sportsify/notification/infrastructure/NotificationRepositoryTest.java
package com.sportsify.notification.infrastructure;

import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Test
    @DisplayName("동일한 eventId + memberId로 알림을 두 번 저장하면 DataIntegrityViolationException이 발생한다")
    void save_중복알림_예외() {
        NotificationEvent event = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        notificationRepository.save(Notification.create(1L, event.getId()));

        assertThatThrownBy(() ->
            notificationRepository.save(Notification.create(1L, event.getId()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("memberId로 알림 목록을 최신순으로 페이징 조회한다")
    void findByMemberIdOrderByCreatedAtDesc_페이징() {
        NotificationEvent event1 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}")
        );
        NotificationEvent event2 = notificationEventRepository.save(
            NotificationEvent.create(NotificationEventType.PAYMENT_COMPLETED, "{}")
        );
        notificationRepository.save(Notification.create(1L, event1.getId()));
        notificationRepository.save(Notification.create(1L, event2.getId()));

        Page<Notification> page = notificationRepository
            .findByMemberIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
```

```java
// src/test/java/com/sportsify/notification/infrastructure/NotificationChannelRepositoryTest.java
package com.sportsify.notification.infrastructure;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationChannelRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationChannelRepository notificationChannelRepository;

    @Test
    @DisplayName("동일한 memberId + channelType으로 채널을 두 번 등록하면 DataIntegrityViolationException이 발생한다")
    void save_중복채널_예외() {
        notificationChannelRepository.save(
            NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com")
        );

        assertThatThrownBy(() ->
            notificationChannelRepository.save(
                NotificationChannel.create(1L, NotificationChannelType.EMAIL, "b@test.com")
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("isEnabled=true인 채널만 조회된다")
    void findByMemberIdAndEnabledTrue_활성채널만() {
        NotificationChannel email = notificationChannelRepository.save(
            NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com")
        );
        NotificationChannel mqtt = notificationChannelRepository.save(
            NotificationChannel.create(1L, NotificationChannelType.MQTT, "client-1")
        );
        mqtt.toggle(); // disabled
        notificationChannelRepository.save(mqtt);

        var channels = notificationChannelRepository.findByMemberIdAndEnabledTrue(1L);

        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).getChannelType()).isEqualTo(NotificationChannelType.EMAIL);
    }
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew test --tests "com.sportsify.notification.infrastructure.*" -i
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/sportsify/notification/domain/repository/ \
        src/main/java/com/sportsify/notification/infrastructure/ \
        src/test/java/com/sportsify/notification/infrastructure/
git commit -m "feat(notification): add repository interfaces and JPA adapters"
```

---

## Task 4: Application DTO + Sender + SSE

**Files:**
- Create: `application/dto/` 3개, `application/sender/` 3개, `application/sse/SseEmitterManager.java`

- [ ] **Step 1: Application DTO 3종 작성**

```java
// NotificationResult.java
package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventType;

import java.time.LocalDateTime;

public record NotificationResult(
        Long id,
        NotificationEventType eventType,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResult of(Notification notification, NotificationEvent event) {
        return new NotificationResult(
                notification.getId(),
                event.getEventType(),
                event.getPayload(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
```

```java
// NotificationSettingResult.java
package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.NotificationSetting;

public record NotificationSettingResult(
        boolean ticketOpenAlert,
        boolean gameStartAlert,
        boolean paymentAlert
) {
    public static NotificationSettingResult from(NotificationSetting setting) {
        return new NotificationSettingResult(
                setting.isTicketOpenAlert(),
                setting.isGameStartAlert(),
                setting.isPaymentAlert()
        );
    }
}
```

```java
// NotificationChannelResult.java
package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;

public record NotificationChannelResult(
        Long id,
        NotificationChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static NotificationChannelResult from(NotificationChannel channel) {
        return new NotificationChannelResult(
                channel.getId(),
                channel.getChannelType(),
                channel.getChannelTarget(),
                channel.isEnabled()
        );
    }
}
```

- [ ] **Step 2: NotificationSender 인터페이스 + 구현체 작성**

```java
// NotificationSender.java
package com.sportsify.notification.application.sender;

import com.sportsify.notification.domain.model.NotificationChannelType;

public interface NotificationSender {
    NotificationChannelType channelType();
    void send(String target, String subject, String body);
}
```

```java
// EmailNotificationSender.java
package com.sportsify.notification.application.sender;

import com.sportsify.notification.domain.model.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void send(String target, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(target);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("EMAIL sent to {}", target);
    }
}
```

```java
// MqttNotificationSender.java
package com.sportsify.notification.application.sender;

import com.sportsify.notification.domain.model.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttNotificationSender implements NotificationSender {

    private final MqttClient mqttClient;

    @Override
    public NotificationChannelType channelType() {
        return NotificationChannelType.MQTT;
    }

    @Override
    public void send(String target, String subject, String body) {
        try {
            MqttMessage message = new MqttMessage(body.getBytes());
            message.setQos(1);
            mqttClient.publish("notification/" + target, message);
            log.info("MQTT published to notification/{}", target);
        } catch (Exception e) {
            throw new RuntimeException("MQTT publish failed", e);
        }
    }
}
```

- [ ] **Step 3: SseEmitterManager 작성**

```java
// SseEmitterManager.java
package com.sportsify.notification.application.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(memberId, emitter);
        emitter.onCompletion(() -> emitters.remove(memberId));
        emitter.onTimeout(() -> emitters.remove(memberId));
        emitter.onError(e -> emitters.remove(memberId));
        log.info("SSE subscribed memberId={}", memberId);
        return emitter;
    }

    public void send(Long memberId, Object data) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("notification").data(data));
        } catch (IOException e) {
            emitters.remove(memberId);
            log.warn("SSE send failed memberId={}", memberId);
        }
    }

    public boolean isConnected(Long memberId) {
        return emitters.containsKey(memberId);
    }
}
```

- [ ] **Step 4: SseEmitterManager 단위 테스트 작성**

```java
// src/test/java/com/sportsify/notification/application/SseEmitterManagerTest.java
package com.sportsify.notification.application;

import com.sportsify.notification.application.sse.SseEmitterManager;
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
        manager.send(999L, "data"); // 예외 없이 skip
    }

    @Test
    @DisplayName("completion 콜백 실행 후 isConnected가 false가 된다")
    void completion_연결해제() {
        SseEmitter emitter = manager.subscribe(1L);
        emitter.complete();
        assertThat(manager.isConnected(1L)).isFalse();
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.sportsify.notification.application.SseEmitterManagerTest" -i
```

Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/sportsify/notification/application/ \
        src/test/java/com/sportsify/notification/application/SseEmitterManagerTest.java
git commit -m "feat(notification): add application DTOs, NotificationSender strategy, SseEmitterManager"
```

---

## Task 5: NotificationEventProcessor (핵심 처리 로직)

**Files:**
- Create: `src/main/java/com/sportsify/notification/application/service/NotificationEventProcessor.java`
- Test: `src/test/java/com/sportsify/notification/application/NotificationEventProcessorTest.java`

- [ ] **Step 1: 단위 테스트 먼저 작성 (TDD)**

```java
// src/test/java/com/sportsify/notification/application/NotificationEventProcessorTest.java
package com.sportsify.notification.application;

import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.application.sse.SseEmitterManager;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock private NotificationEventRepository eventRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingRepository settingRepository;
    @Mock private NotificationChannelRepository channelRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private SseEmitterManager sseEmitterManager;
    @Mock private NotificationSender emailSender;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        given(emailSender.channelType()).willReturn(NotificationChannelType.EMAIL);
        processor = new NotificationEventProcessor(
            eventRepository, notificationRepository, settingRepository,
            channelRepository, historyRepository, sseEmitterManager,
            List.of(emailSender)
        );
    }

    @Test
    @DisplayName("알림 설정이 OFF인 회원은 알림이 생성되지 않는다")
    void process_알림설정OFF_스킵() {
        NotificationEvent event = NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}");
        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue()).willReturn(List.of());

        processor.process(NotificationEventType.TICKET_OPEN, "{}");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 동일 eventId+memberId 알림이 존재하면 발송하지 않는다")
    void process_중복알림_스킵() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(true);

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 성공 시 NotificationHistory에 SENT 상태로 저장된다")
    void process_발송성공_SENT저장() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        Notification notification = notificationWithId(100L, 1L, 10L);
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com");

        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(1L)).willReturn(List.of(channel));
        doNothing().when(emailSender).send(any(), any(), any());

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(historyRepository).save(argThat(h -> h.getStatus() == NotificationSendStatus.SENT));
    }

    @Test
    @DisplayName("발송 3회 실패 시 NotificationHistory에 FAILED 상태로 저장된다")
    void process_발송3회실패_FAILED저장() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        Notification notification = notificationWithId(100L, 1L, 10L);
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com");

        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(1L)).willReturn(List.of(channel));
        doThrow(new RuntimeException("SMTP 오류")).when(emailSender).send(any(), any(), any());

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(emailSender, times(3)).send(any(), any(), any());
        verify(historyRepository).save(argThat(h -> h.getStatus() == NotificationSendStatus.FAILED));
    }

    // ── 픽스처 헬퍼 ──

    private NotificationEvent notificationEventWithId(Long id, NotificationEventType type) {
        NotificationEvent event = NotificationEvent.create(type, "{}");
        setId(event, id);
        return event;
    }

    private Notification notificationWithId(Long id, Long memberId, Long eventId) {
        Notification notification = Notification.create(memberId, eventId);
        setId(notification, id);
        return notification;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.sportsify.notification.application.NotificationEventProcessorTest" -i
```

Expected: FAIL with "cannot find symbol: NotificationEventProcessor"

- [ ] **Step 3: NotificationEventProcessor 구현**

```java
// NotificationEventProcessor.java
package com.sportsify.notification.application.service;

import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.application.sse.SseEmitterManager;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationEventProcessor {

    private static final int MAX_RETRY = 3;

    private final NotificationEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final SseEmitterManager sseEmitterManager;
    private final Map<NotificationChannelType, NotificationSender> senderMap;

    public NotificationEventProcessor(
            NotificationEventRepository eventRepository,
            NotificationRepository notificationRepository,
            NotificationSettingRepository settingRepository,
            NotificationChannelRepository channelRepository,
            NotificationHistoryRepository historyRepository,
            SseEmitterManager sseEmitterManager,
            List<NotificationSender> senders
    ) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.channelRepository = channelRepository;
        this.historyRepository = historyRepository;
        this.sseEmitterManager = sseEmitterManager;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
    }

    @Transactional
    public void process(NotificationEventType eventType, String payload) {
        NotificationEvent event = eventRepository.save(NotificationEvent.create(eventType, payload));

        List<Long> targetMemberIds = resolveTargetMemberIds(eventType);
        if (targetMemberIds.isEmpty()) {
            event.markPublished();
            return;
        }

        boolean anyFailed = false;
        for (Long memberId : targetMemberIds) {
            if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) {
                continue;
            }
            Notification notification = notificationRepository.save(Notification.create(memberId, event.getId()));
            sseEmitterManager.send(memberId, eventType.name());

            List<NotificationChannel> channels = channelRepository.findByMemberIdAndEnabledTrue(memberId);
            for (NotificationChannel channel : channels) {
                boolean sent = sendWithRetry(notification.getId(), channel, eventType.name(), payload);
                if (!sent) {
                    anyFailed = true;
                }
            }
        }

        if (anyFailed) {
            event.markFailed();
        } else {
            event.markPublished();
        }
    }

    private List<Long> resolveTargetMemberIds(NotificationEventType eventType) {
        return switch (eventType) {
            case TICKET_OPEN -> settingRepository.findMemberIdsByTicketOpenAlertTrue();
            case GAME_START -> settingRepository.findMemberIdsByGameStartAlertTrue();
            case PAYMENT_COMPLETED -> settingRepository.findMemberIdsByPaymentAlertTrue();
            case CHAT_MENTION -> List.of();
        };
    }

    private boolean sendWithRetry(Long notificationId, NotificationChannel channel, String subject, String body) {
        NotificationSender sender = senderMap.get(channel.getChannelType());
        if (sender == null) {
            return true;
        }
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                sender.send(channel.getChannelTarget(), subject, body);
                historyRepository.save(NotificationHistory.sent(notificationId, channel.getChannelType()));
                return true;
            } catch (Exception e) {
                log.warn("알림 발송 실패 attempt={}/{} channel={} error={}", attempt, MAX_RETRY, channel.getChannelType(), e.getMessage());
                if (attempt == MAX_RETRY) {
                    historyRepository.save(NotificationHistory.failed(notificationId, channel.getChannelType(), e.getMessage()));
                }
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.sportsify.notification.application.NotificationEventProcessorTest" -i
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/sportsify/notification/application/service/NotificationEventProcessor.java \
        src/test/java/com/sportsify/notification/application/NotificationEventProcessorTest.java
git commit -m "feat(notification): implement NotificationEventProcessor with retry and idempotency"
```

---

## Task 6: NotificationService + NotificationSettingService

**Files:**
- Create: `application/service/NotificationService.java`
- Create: `application/service/NotificationSettingService.java`

- [ ] **Step 1: 단위 테스트 작성**

```java
// src/test/java/com/sportsify/notification/application/NotificationServiceTest.java
package com.sportsify.notification.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventRepository eventRepository;

    @Test
    @DisplayName("읽지 않은 알림을 읽음 처리한다")
    void markRead_성공() {
        Notification notification = Notification.create(1L, 10L);
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(notification));

        notificationService.markRead(1L, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리하면 NOTIFICATION_ALREADY_READ 예외가 발생한다")
    void markRead_이미읽음_예외() {
        Notification notification = Notification.create(1L, 10L);
        notification.markRead();
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ALREADY_READ);
    }

    @Test
    @DisplayName("존재하지 않는 알림 읽음 처리 시 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void markRead_없는알림_예외() {
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
```

```java
// src/test/java/com/sportsify/notification/application/NotificationSettingServiceTest.java
package com.sportsify.notification.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @InjectMocks
    private NotificationSettingService settingService;

    @Mock
    private NotificationSettingRepository settingRepository;

    @Mock
    private NotificationChannelRepository channelRepository;

    @Test
    @DisplayName("존재하지 않는 알림 설정 조회 시 NOTIFICATION_SETTING_NOT_FOUND 예외가 발생한다")
    void getSetting_없음_예외() {
        given(settingRepository.findByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingService.getSetting(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 등록된 채널 타입으로 등록 시 NOTIFICATION_CHANNEL_ALREADY_EXISTS 예외가 발생한다")
    void registerChannel_중복채널_예외() {
        given(channelRepository.existsByMemberIdAndChannelType(1L, NotificationChannelType.EMAIL)).willReturn(true);

        assertThatThrownBy(() -> settingService.registerChannel(1L, NotificationChannelType.EMAIL, "a@test.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("존재하지 않는 채널 삭제 시 NOTIFICATION_CHANNEL_NOT_FOUND 예외가 발생한다")
    void deleteChannel_없음_예외() {
        given(channelRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingService.deleteChannel(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.sportsify.notification.application.NotificationServiceTest" \
               --tests "com.sportsify.notification.application.NotificationSettingServiceTest" -i
```

Expected: FAIL with "cannot find symbol"

- [ ] **Step 3: NotificationService 구현**

```java
// NotificationService.java
package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.application.sse.SseEmitterManager;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository eventRepository;
    private final SseEmitterManager sseEmitterManager;

    public Page<NotificationResult> getNotifications(Long memberId, Pageable pageable) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(n -> {
                    NotificationEvent event = eventRepository.findById(n.getEventId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
                    return NotificationResult.of(n, event);
                });
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.isAlreadyRead()) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ);
        }
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllReadByMemberId(memberId);
    }

    public SseEmitter subscribe(Long memberId) {
        return sseEmitterManager.subscribe(memberId);
    }
}
```

- [ ] **Step 4: NotificationSettingService 구현**

```java
// NotificationSettingService.java
package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.application.dto.NotificationSettingResult;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final NotificationChannelRepository channelRepository;

    public NotificationSettingResult getSetting(Long memberId) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        return NotificationSettingResult.from(setting);
    }

    @Transactional
    public NotificationSettingResult updateSetting(Long memberId, boolean ticketOpenAlert, boolean gameStartAlert, boolean paymentAlert) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        setting.update(ticketOpenAlert, gameStartAlert, paymentAlert);
        return NotificationSettingResult.from(setting);
    }

    public List<NotificationChannelResult> getChannels(Long memberId) {
        return channelRepository.findByMemberIdAndEnabledTrue(memberId)
                .stream()
                .map(NotificationChannelResult::from)
                .toList();
    }

    @Transactional
    public NotificationChannelResult registerChannel(Long memberId, NotificationChannelType channelType, String channelTarget) {
        if (channelRepository.existsByMemberIdAndChannelType(memberId, channelType)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS);
        }
        NotificationChannel channel = channelRepository.save(
                NotificationChannel.create(memberId, channelType, channelTarget)
        );
        return NotificationChannelResult.from(channel);
    }

    @Transactional
    public void deleteChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        if (!channel.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        channelRepository.delete(channel);
    }

    @Transactional
    public NotificationChannelResult toggleChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        if (!channel.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        channel.toggle();
        return NotificationChannelResult.from(channel);
    }
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.sportsify.notification.application.NotificationServiceTest" \
               --tests "com.sportsify.notification.application.NotificationSettingServiceTest" -i
```

Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/sportsify/notification/application/service/ \
        src/test/java/com/sportsify/notification/application/NotificationServiceTest.java \
        src/test/java/com/sportsify/notification/application/NotificationSettingServiceTest.java
git commit -m "feat(notification): implement NotificationService and NotificationSettingService"
```

---

## Task 7: Redis Streams Consumer + Infrastructure Config

**Files:**
- Create: `application/consumer/NotificationStreamConsumer.java`
- Create: `infrastructure/config/RedisStreamsConfig.java`
- Create: `infrastructure/config/MailConfig.java`
- Create: `infrastructure/config/MqttConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: RedisStreamsConfig 작성**

```java
// RedisStreamsConfig.java
package com.sportsify.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class RedisStreamsConfig {

    private static final String GROUP = "notification-group";
    private static final List<String> STREAM_KEYS = List.of(
            "ticket.opened", "payment.completed", "game.starting", "chat.mentioned"
    );

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate
    ) {
        initConsumerGroups(redisTemplate);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
        return container;
    }

    private void initConsumerGroups(StringRedisTemplate redisTemplate) {
        for (String streamKey : STREAM_KEYS) {
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), GROUP);
            } catch (Exception e) {
                log.debug("Consumer group already exists for stream={}", streamKey);
            }
        }
    }
}
```

- [ ] **Step 2: NotificationStreamConsumer 작성**

```java
// NotificationStreamConsumer.java
package com.sportsify.notification.application.consumer;

import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.domain.model.NotificationEventType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamConsumer {

    private static final String GROUP = "notification-group";
    private static final Map<String, NotificationEventType> STREAM_TO_EVENT = Map.of(
            "ticket.opened", NotificationEventType.TICKET_OPEN,
            "payment.completed", NotificationEventType.PAYMENT_COMPLETED,
            "game.starting", NotificationEventType.GAME_START,
            "chat.mentioned", NotificationEventType.CHAT_MENTION
    );

    private final StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final NotificationEventProcessor processor;

    @Value("${spring.application.name:app}-${random.uuid}")
    private String consumerName;

    @PostConstruct
    public void registerListeners() {
        for (Map.Entry<String, NotificationEventType> entry : STREAM_TO_EVENT.entrySet()) {
            String streamKey = entry.getKey();
            NotificationEventType eventType = entry.getValue();

            container.receive(
                    Consumer.from(GROUP, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> handleMessage(streamKey, eventType, message)
            );
        }
    }

    private void handleMessage(String streamKey, NotificationEventType eventType, ObjectRecord<String, String> message) {
        try {
            processor.process(eventType, message.getValue());
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, message.getId());
            log.info("Stream ACK streamKey={} id={}", streamKey, message.getId());
        } catch (Exception e) {
            log.error("Stream processing failed streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }
}
```

- [ ] **Step 3: MailConfig 작성**

```java
// MailConfig.java
package com.sportsify.notification.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return mailSender;
    }
}
```

- [ ] **Step 4: MqttConfig 작성**

```java
// MqttConfig.java
package com.sportsify.notification.infrastructure.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id:notification-service}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        client.connect(options);
        return client;
    }
}
```

- [ ] **Step 5: application.yml에 mail + mqtt 설정 추가**

`src/main/resources/application.yml`에 아래 블록 추가 (기존 설정 아래):

```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}

mqtt:
  broker-url: ${MQTT_BROKER_URL:tcp://localhost:1883}
  client-id: ${MQTT_CLIENT_ID:notification-service}
  username: ${MQTT_USERNAME:}
  password: ${MQTT_PASSWORD:}
```

- [ ] **Step 6: 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/sportsify/notification/application/consumer/ \
        src/main/java/com/sportsify/notification/infrastructure/config/ \
        src/main/resources/application.yml
git commit -m "feat(notification): add Redis Streams consumer, Mail/MQTT config"
```

---

## Task 8: Presentation 계층 (Controller + API 인터페이스)

**Files:**
- Create: `presentation/` 하위 6개 파일
- Modify: `infrastructure/config/SecurityConfig.java`

- [ ] **Step 1: Presentation DTO 작성**

```java
// NotificationResponse.java
package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.domain.model.NotificationEventType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationEventType eventType,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(
                result.id(),
                result.eventType(),
                result.payload(),
                result.read(),
                result.createdAt()
        );
    }
}
```

```java
// NotificationSettingResponse.java
package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationSettingResult;

public record NotificationSettingResponse(
        boolean ticketOpenAlert,
        boolean gameStartAlert,
        boolean paymentAlert
) {
    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.ticketOpenAlert(),
                result.gameStartAlert(),
                result.paymentAlert()
        );
    }
}
```

```java
// UpdateNotificationSettingRequest.java
package com.sportsify.notification.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull Boolean ticketOpenAlert,
        @NotNull Boolean gameStartAlert,
        @NotNull Boolean paymentAlert
) {}
```

```java
// NotificationChannelResponse.java
package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.domain.model.NotificationChannelType;

public record NotificationChannelResponse(
        Long id,
        NotificationChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static NotificationChannelResponse from(NotificationChannelResult result) {
        return new NotificationChannelResponse(
                result.id(),
                result.channelType(),
                result.channelTarget(),
                result.enabled()
        );
    }
}
```

```java
// RegisterChannelRequest.java
package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.domain.model.NotificationChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterChannelRequest(
        @NotNull NotificationChannelType channelType,
        @NotBlank String channelTarget
) {}
```

- [ ] **Step 2: Swagger API 인터페이스 작성**

```java
// NotificationApi.java
package com.sportsify.notification.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "Notification", description = "알림 인박스 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationApi {

    @SwaggerApi(summary = "알림 목록 조회", description = "최신순 페이징 반환.")
    ResponseEntity<Page<NotificationResponse>> getNotifications(Long memberId, Pageable pageable);

    @SwaggerApi(summary = "알림 읽음 처리", description = "단건 읽음 처리.",
            errors = {NOTIFICATION_NOT_FOUND, NOTIFICATION_ALREADY_READ})
    ResponseEntity<Void> markRead(Long memberId, Long notificationId);

    @SwaggerApi(summary = "전체 읽음 처리", description = "미읽음 알림 전체 읽음 처리.",
            responseCode = "204", responseDescription = "성공 (본문 없음)")
    ResponseEntity<Void> markAllRead(Long memberId);

    @SwaggerApi(summary = "SSE 연결", description = "실시간 알림 수신용 SSE 스트림.")
    SseEmitter subscribe(Long memberId);
}
```

```java
// NotificationSettingApi.java
package com.sportsify.notification.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.notification.presentation.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "NotificationSetting", description = "알림 설정 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationSettingApi {

    @SwaggerApi(summary = "알림 설정 조회", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> getSetting(Long memberId);

    @SwaggerApi(summary = "알림 설정 수정", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> updateSetting(Long memberId, @RequestBody UpdateNotificationSettingRequest request);

    @SwaggerApi(summary = "채널 목록 조회")
    ResponseEntity<List<NotificationChannelResponse>> getChannels(Long memberId);

    @SwaggerApi(summary = "채널 등록", error = NOTIFICATION_CHANNEL_ALREADY_EXISTS)
    ResponseEntity<NotificationChannelResponse> registerChannel(Long memberId, @RequestBody RegisterChannelRequest request);

    @SwaggerApi(summary = "채널 삭제", responseCode = "204", responseDescription = "성공 (본문 없음)",
            error = NOTIFICATION_CHANNEL_NOT_FOUND)
    ResponseEntity<Void> deleteChannel(Long memberId, Long channelId);

    @SwaggerApi(summary = "채널 활성화/비활성화 토글", error = NOTIFICATION_CHANNEL_NOT_FOUND)
    ResponseEntity<NotificationChannelResponse> toggleChannel(Long memberId, Long channelId);
}
```

- [ ] **Step 3: Controller 2종 작성**

```java
// NotificationController.java
package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.presentation.api.NotificationApi;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                notificationService.getNotifications(memberId, pageable)
                        .map(NotificationResponse::from)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.markAllRead(memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal Long memberId
    ) {
        return notificationService.subscribe(memberId);
    }
}
```

```java
// NotificationSettingController.java
package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.api.NotificationSettingApi;
import com.sportsify.notification.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSettingController implements NotificationSettingApi {

    private final NotificationSettingService settingService;

    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> getSetting(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(NotificationSettingResponse.from(settingService.getSetting(memberId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> updateSetting(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateNotificationSettingRequest request
    ) {
        return ResponseEntity.ok(NotificationSettingResponse.from(
                settingService.updateSetting(memberId, request.ticketOpenAlert(), request.gameStartAlert(), request.paymentAlert())
        ));
    }

    @GetMapping("/channels")
    public ResponseEntity<List<NotificationChannelResponse>> getChannels(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                settingService.getChannels(memberId).stream()
                        .map(NotificationChannelResponse::from)
                        .toList()
        );
    }

    @PostMapping("/channels")
    public ResponseEntity<NotificationChannelResponse> registerChannel(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RegisterChannelRequest request
    ) {
        return ResponseEntity.ok(NotificationChannelResponse.from(
                settingService.registerChannel(memberId, request.channelType(), request.channelTarget())
        ));
    }

    @DeleteMapping("/channels/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        settingService.deleteChannel(memberId, channelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/channels/{channelId}/toggle")
    public ResponseEntity<NotificationChannelResponse> toggleChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        return ResponseEntity.ok(NotificationChannelResponse.from(
                settingService.toggleChannel(memberId, channelId)
        ));
    }
}
```

- [ ] **Step 4: SecurityConfig에 SSE 엔드포인트 허용 추가**

`SecurityConfig.java`의 `permitAll()` 목록에 추가:

```java
"/api/v1/notifications/stream"
```

- [ ] **Step 5: API 테스트 작성**

```java
// src/test/java/com/sportsify/notification/presentation/NotificationControllerApiTest.java
package com.sportsify.notification.presentation;

import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({
    com.sportsify.notification.presentation.controller.NotificationController.class,
    com.sportsify.notification.presentation.controller.NotificationSettingController.class
})
class NotificationControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("GET /api/v1/notifications — 인증 없이 호출 시 401 반환")
    void getNotifications_미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read — 인증 후 204 반환")
    void markRead_인증후_204() throws Exception {
        String token = bearerToken(1L, "USER");

        mockMvc.perform(patch("/api/v1/notifications/1/read")
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream — Content-Type이 text/event-stream이다")
    void subscribe_SSE_contentType() throws Exception {
        String token = bearerToken(1L, "USER");
        given(notificationService.subscribe(1L))
                .willReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
```

```java
// src/test/java/com/sportsify/notification/presentation/NotificationSettingControllerApiTest.java
package com.sportsify.notification.presentation;

import com.sportsify.notification.application.dto.NotificationSettingResult;
import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.dto.UpdateNotificationSettingRequest;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({
    com.sportsify.notification.presentation.controller.NotificationController.class,
    com.sportsify.notification.presentation.controller.NotificationSettingController.class
})
class NotificationSettingControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("PUT /api/v1/notifications/settings — 요청 바디 없으면 400 반환")
    void updateSetting_바디없음_400() throws Exception {
        String token = bearerToken(1L, "USER");

        mockMvc.perform(put("/api/v1/notifications/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/settings — 정상 요청 시 200 반환")
    void updateSetting_정상요청_200() throws Exception {
        String token = bearerToken(1L, "USER");
        given(notificationSettingService.updateSetting(any(), any(), any(), any()))
                .willReturn(new NotificationSettingResult(true, false, true));

        mockMvc.perform(put("/api/v1/notifications/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateNotificationSettingRequest(true, false, true)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketOpenAlert").value(true))
                .andExpect(jsonPath("$.gameStartAlert").value(false));
    }
}
```

- [ ] **Step 6: 테스트 실행**

```bash
./gradlew test --tests "com.sportsify.notification.presentation.*" -i
```

Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/sportsify/notification/presentation/ \
        src/main/java/com/sportsify/infrastructure/config/SecurityConfig.java \
        src/test/java/com/sportsify/notification/presentation/
git commit -m "feat(notification): add presentation layer, controllers, SSE endpoint"
```

---

## Task 9: MemberService 연동 — 회원가입 시 NotificationSetting 자동 생성

**Files:**
- Modify: `src/main/java/com/sportsify/member/application/service/AuthService.java`
- Modify: `src/main/java/com/sportsify/member/application/service/MemberService.java` (의존성 주입)

- [ ] **Step 1: AuthService에서 회원 신규 생성 시 NotificationSetting 자동 생성 추가**

`AuthService.java`에서 신규 회원 저장 직후 아래 코드 추가:

```java
// AuthService.java 상단 필드 추가
private final NotificationSettingRepository notificationSettingRepository;

// 신규 회원 저장 직후 (memberRepository.save(newMember) 아래):
notificationSettingRepository.save(NotificationSetting.createDefault(savedMember.getId()));
```

> `AuthService`의 `@RequiredArgsConstructor`가 자동 주입 처리. `NotificationSettingRepository` import 필요.

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/sportsify/member/application/service/AuthService.java
git commit -m "feat(notification): auto-create NotificationSetting on member registration"
```

---

## Task 10: 전체 테스트 실행 + 최종 정리

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test -i
```

Expected: BUILD SUCCESSFUL, all tests passed

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 최종 커밋**

```bash
git add -A
git status  # 누락 파일 없는지 확인
git commit -m "feat(notification): complete notification domain implementation"
```

---

## Self-Review 체크리스트

- [x] **Spec coverage:** 설계 문서 10개 섹션 모두 커버됨
  - 발송 채널 EMAIL/MQTT → Task 4 (EmailNotificationSender, MqttNotificationSender)
  - 이벤트 타입 4종 → Task 2 (NotificationEventType enum)
  - SSE 인박스 → Task 4 (SseEmitterManager) + Task 8 (Controller)
  - 재시도 3회 → Task 5 (NotificationEventProcessor.sendWithRetry)
  - 설정 API → Task 6 (NotificationSettingService) + Task 8
  - DB UNIQUE 중복 방지 → Task 1 (V2 migration) + Task 2 (Notification entity)
  - 회원가입 시 설정 자동 생성 → Task 9
- [x] **Placeholder 없음:** 모든 step에 실제 코드 포함
- [x] **타입 일관성:** `NotificationEventProcessor` 생성자 파라미터, `NotificationSender.send()` 시그니처, `NotificationResult.of()` — 전 Task 일관
- [x] **멱등성 보장:** DB UNIQUE(event_id, member_id) + `existsByEventIdAndMemberId` 체크 → Task 2, 3, 5에서 일관
