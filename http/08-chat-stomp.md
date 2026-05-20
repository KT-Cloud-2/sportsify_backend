# 채팅 STOMP 연결 가이드

`.http` 파일은 HTTP REST만 지원하므로 STOMP 실시간 채팅은 별도 도구가 필요합니다.

## 엔드포인트

| 항목 | 값 |
|------|-----|
| WebSocket URL | `ws://localhost:8080/ws/chat/websocket` |
| SockJS URL | `http://localhost:8080/ws/chat` |
| 메시지 prefix | `/app` |
| 구독 prefix | `/topic`, `/queue` |

## STOMP 프레임 순서

### 1. CONNECT (토큰 인증)
```
CONNECT
Authorization:Bearer {accessToken}
accept-version:1.2
heart-beat:10000,10000

^@
```

### 2. SUBSCRIBE (채팅방 구독)
```
SUBSCRIBE
id:sub-0
destination:/topic/room/{roomId}

^@
```

### 3. 메시지 전송
```
SEND
destination:/app/chat.send
content-type:application/json

{"roomId":1,"content":"안녕하세요","clientMessageId":"uuid-here","type":"TEXT"}
^@
```

### 4. 읽음 처리
```
SEND
destination:/app/chat.read
content-type:application/json

{"roomId":1,"lastReadMessageId":42}
^@
```

### 5. 타이핑 인디케이터
```
SEND
destination:/app/chat.typing
content-type:application/json

{"roomId":1}
^@
```

## 권장 테스트 도구

### Postman
- New → WebSocket Request → `ws://localhost:8080/ws/chat/websocket`
- Messages 탭에서 STOMP 프레임 직접 입력

### wscat (CLI)
```bash
npm install -g wscat
wscat -c "ws://localhost:8080/ws/chat/websocket"
```

### IntelliJ 플러그인
- **STOMP over WebSocket** 플러그인 설치 후 사용 가능

## 인증 흐름 요약

1. `00-dev-token.http` 실행 → `accessToken` 획득
2. STOMP CONNECT 프레임의 `Authorization: Bearer {accessToken}` 헤더로 전달
3. 토큰 없이 CONNECT 하면 익명 연결 허용 (게임 채팅방 구독만 가능)
4. 메시지 전송(SEND)은 인증 필수
