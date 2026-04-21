# Sortsify — 기능 구현 현황

> 체크박스로 구현 완료 여부를 관리한다.  
> 담당자별로 자기 도메인 항목만 업데이트한다.

---


## 공통 인프라

- [ ] Docker Compose (PostgreSQL, Redis, Kafka, Zookeeper)
- [ ] Spring Security + JWT 인증 필터
- [ ] Swagger / OpenAPI 3.0 설정
- [ ] 공통 응답 포맷 (`ApiResponse`)
- [ ] 공통 예외 핸들러 (`GlobalExceptionHandler`)
- [ ] Kafka Producer / Consumer 기본 설정
- [ ] Redis 설정
- [ ] GitHub Actions CI (빌드 + 테스트)

---

## Member — 오예진

> 회원가입 / 로그인은 OAuth2 소셜 로그인 전용. 직접 회원가입 없음.  
> 선호 팀 설정은 필수가 아니며, 여러 팀을 우선순위로 관리한다.

### MVP
- [ ] 소셜 로그인 (회원가입 겸용) `GET /oauth2/authorization/{registrationId}`
  - [ ] Google
  - [ ] Kakao
- [ ] 로그아웃 `POST /api/members/logout`
- [ ] 선호 팀 추가 (선택) `POST /api/members/me/favorite-teams`
- [ ] 선호 팀 목록 조회 `GET /api/members/me/favorite-teams`
- [ ] 선호 팀 삭제 `DELETE /api/members/me/favorite-teams/{teamId}`

### 고급\
- [ ] 응원 활동 통계 `GET /api/members/me/activity/monthly`

---

## Ticketing — 손하영

> 경기는 team1_id / team2_id 구조. seats 는 team_side (TEAM1, TEAM2, NEUTRAL) 필드 포함.

### MVP
- [ ] 경기 목록 조회 `GET /api/games` (sportType, teamId, status 필터)
- [ ] 경기 상세 조회 `GET /api/games/{gameId}` (team1/team2 정보 포함)
- [ ] 좌석 목록 조회 `GET /api/games/{gameId}/seats` (teamSide, grade 필터)
- [ ] 티켓 예매 `POST /api/tickets/reserve`

### 고급
- [ ] 대기열 입장 `POST /api/tickets/queue/enter`
- [ ] 대기열 순번 조회 `GET /api/tickets/queue/position`
- [ ] 대기열 이탈 `DELETE /api/tickets/queue/leave`
- [ ] 좌석 선점 타임아웃 (10분 TTL + `@Scheduled` 해제)
- [ ] 티켓 취소 `POST /api/tickets/{ticketId}/cancel`
- [ ] 동적 가격 책정 (잔여 좌석율 · 시간대 · 라이벌전 가중치)

---

## Payment — 유창민

### MVP
- [ ] 결제 요청 `POST /api/payments/request` (토스페이먼츠 / 아임포트 테스트)
- [ ] 결제 검증 `POST /api/payments/verify`
  - [ ] 금액 위변조 확인
  - [ ] Idempotency Key 중복 방지
  - [ ] 좌석 유효성 확인
- [ ] PG Webhook 수신 `POST /api/payments/webhook`

### 고급
- [ ] 결제 타임아웃 처리 (10분 → 자동 취소 + 좌석 해제)
- [ ] 환불 `POST /api/payments/{paymentId}/refund`

---

## Chat — 주병규

### MVP
- [ ] 채팅방 목록 조회 `GET /api/chat/rooms`
- [ ] 팀별 채팅방 조회 `GET /api/chat/rooms/team/{teamName}`
- [ ] WebSocket 연결 / 구독 / 메시지 전송 (STOMP)
- [ ] Redis Pub/Sub 멀티 서버 브로드캐스트

### 고급
- [ ] 응원 이모지 / 액션 (FIRE, CLAP, HEART, FLAG)
- [ ] 욕설 필터링 (`ProfanityFilter`)
- [ ] 경고 누적 3회 → 24시간 채팅 금지
- [ ] 응원 열기 지수 `GET /api/chat/intensity/{gameId}`
- [ ] 채팅 이력 저장 / 조회 `GET /api/chat/history/{roomId}`

---

## Notification — 강정훈

> notification_settings (알림 종류 ON/OFF) 와 notification_channels (채널별 수신 대상) 분리 구조.

### MVP
- [ ] 알림 설정 조회 `GET /api/notifications/settings`
- [ ] 알림 설정 변경 `PUT /api/notifications/settings` (ticketOpenAlert, gameStartAlert, paymentAlert)
- [ ] 알림 채널 목록 조회 `GET /api/notifications/channels`
- [ ] 알림 채널 추가 `POST /api/notifications/channels`
- [ ] 알림 채널 수정 `PUT /api/notifications/channels/{channel}`
- [ ] 알림 채널 삭제 `DELETE /api/notifications/channels/{channel}`
- [ ] Kafka Consumer 기반 알림 발송
  - [ ] `ticket.opened` → ticket_open_alert=true 회원에게 발송
  - [ ] `payment.completed` → payment_alert=true 회원에게 발송
  - [ ] `game.starting` → game_start_alert=true 회원에게 발송
  - [ ] `chat.mentioned` → 멘션 알림

### 고급
- [ ] Email 발송 (JavaMailSender) — channel=EMAIL
- [ ] SMS 발송 (CoolSMS / Twilio) — channel=SMS
- [ ] Discord Webhook 발송 — channel=DISCORD
- [ ] FCM Push 발송 — channel=PUSH
- [ ] 알림 템플릿 엔진
- [ ] 알림 발송 이력 `GET /api/notifications/history`
- [ ] 배치 알림 (매일 오후 6시 일일 다이제스트)

---

## Teams (공통 마스터)

> 팀 데이터는 sport_type 으로 종목을 구분. 동일 조직의 다른 종목 팀은 별도 행으로 관리.

### MVP
- [ ] 팀 목록 조회 `GET /api/teams` (sportType, isActive 필터)
- [ ] 팀 상세 조회 `GET /api/teams/{teamId}`

---

## 기술 챌린지

- [ ] 동시성 제어 3종 비교 (비관적 락 vs 낙관적 락 vs Redis 분산 락)
- [ ] 대기열 방식 비교 (Redis Sorted Set vs Kafka Queue)
- [ ] 캐싱 3단 구조 (Caffeine L1 → Redis L2 → DB L3)
- [ ] 이벤트 소싱 패턴 (티켓 예매 전 과정 이벤트 로그)
- [ ] 부하 테스트 JMeter 시나리오 작성
- [ ] Prometheus + Grafana 모니터링 대시보드
- [ ] ELK Stack 로그 수집

---

## 문서 / 산출물

- [x] 기획안 `docs/01-project-overview.md`
- [x] ERD `docs/02-erd.md`
- [x] 팀 규칙 `docs/03-team-rules.md`
- [x] API 명세서 `docs/04-api-spec.md`
- [x] 테스트 시나리오 `docs/05-test-scenarios.md`
- [x] 백엔드 개발 문서 `docs/06-backend-dev.md`
- [ ] Postman Collection `tools/sortsify.postman_collection.json`
- [ ] 부하 테스트 결과 리포트
- [ ] 발표 자료
