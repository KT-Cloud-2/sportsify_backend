/**
 * [A] 메시지 전송 부하 테스트 — Ramping 패턴
 *
 * 시나리오: VU 마다 WebSocket 연결 → 구독 ACK 대기 → 메시지 N건 전송 → 연결 종료
 * 목적:     VU 증가에 따라 메시지 처리량과 레이턴시가 어떻게 변하는지 측정
 *
 * 실행:
 *   k6 run k6/chat/ramping.js
 *   k6 run -e BASE_URL=http://staging.example.com k6/chat/ramping.js
 *
 * 사전 조건:
 *   - k6/chat/seed.sql 을 실행해 멤버·방·멤버십 데이터를 미리 적재한다
 *     예) ./k6/chat/run.sh ramping
 *   - /dev/token?memberId={id} 엔드포인트가 활성화되어 있어야 한다 (local 프로파일)
 */

import ws from 'k6/ws';
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';
import {stomp} from './lib/stomp.js';

// ── 커스텀 메트릭 ────────────────────────────────────────────
const stompConnectTime = new Trend('stomp_connect_ms', true);
const msgSent = new Counter('stomp_messages_sent');
const msgReceived = new Counter('stomp_messages_received');

// ── 환경 변수 ────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

// ── 설정 상수 ────────────────────────────────────────────────
const VU_BUFFER = 520;    // 최대 VU(500) + 여유
const MEMBER_OFFSET = 20000;  // seed.sql 과 일치 (constant.js 30000번대와 충돌 방지)
const MSG_INTERVAL_MS = 5000;   // 구독 유지 중 메시지 전송 주기 (ms)
const TOTAL_DURATION_MS = 11 * 60 * 1000; // stages 합산 (2+2+2+5분) + 안전 여유

// seed.sql 이 삽입한 고정 룸 ID (9021 ~ 9040)
const ROOM_IDS = Array.from({length: 20}, (_, i) => 9021 + i);

export const options = {
    stages: [
        {duration: '2m', target: 300},
        {duration: '2m', target: 400},
        {duration: '2m', target: 500},
        {duration: '5m', target: 500},
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        ws_connecting: ['p(95)<3000'],
        ws_msgs_received: ['rate>0'],
        iteration_duration: ['p(95)<3000'],
    },
};

export function setup() {
    const tokens = [];
    for (let i = 0; i < VU_BUFFER; i++) {
        const res = http.get(`${BASE_URL}/dev/token?memberId=${MEMBER_OFFSET + i}`);
        check(res, {'[setup] token issued': (r) => r.status === 200});
        if (res.status === 200) {
            tokens.push(JSON.parse(res.body).token);
        }
        if (i % 100 === 99) sleep(0.2);
    }
    return {tokens};
}

export default function (data) {
    const {tokens} = data;
    const token = tokens[(__VU - 1) % tokens.length];
    const roomId = ROOM_IDS[(__VU - 1) % ROOM_IDS.length];

    const state = {
        connectedAt: 0,
        subscribed: false,
    };

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => {
            state.connectedAt = Date.now();
            socket.send(stomp.connect(token));
        });

        socket.on('message', (raw) => {
            const cmd = stomp.command(raw);

            if (cmd === 'CONNECTED') {
                stompConnectTime.add(Date.now() - state.connectedAt);
                socket.send(stomp.subscribe('sub-0', `/topic/rooms/${roomId}`));

            } else if (cmd === 'MESSAGE') {
                const body = stomp.parseBody(raw);

                if (!state.subscribed) {
                    if (body?.type === 'SUBSCRIBE_FAILED') {
                        socket.close();
                        return;
                    }
                    if (body?.type === 'SUBSCRIBED') {
                        state.subscribed = true;
                        socket.setInterval(() => {
                            socket.send(stomp.send('/app/chat.send', {
                                clientMessageId: `k6-ramp-${__VU}-${Date.now()}`,
                                roomId: roomId,
                                type: 'TEXT',
                                content: 'ramping test msg',
                            }));
                            msgSent.add(1);
                        }, MSG_INTERVAL_MS);
                    }
                    return;
                }

                if (body?.event === 'MESSAGE_SENT') {
                    msgReceived.add(1);
                }

            } else if (cmd === 'ERROR') {
                socket.close();
            }
        });

        socket.on('error', () => socket.close());

        socket.setTimeout(() => socket.close(), TOTAL_DURATION_MS + 60_000);
    });

    check(res, {'ws handshake 101': (r) => r && r.status === 101});
}
