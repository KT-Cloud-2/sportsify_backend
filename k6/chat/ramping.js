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
 *
 * 측정 지표 (단계별):
 *   1. 연결       : ws_connecting, stomp_connect_ms
 *   2. 구독       : stomp_subscribe_ms, stomp_subscribe_success
 *   3. 메시지 송수신: stomp_messages_sent, stomp_message_roundtrip_ms
 */

import ws from 'k6/ws';
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';
import {stomp} from './lib/stomp.js';

// 1. 연결
const stompConnectTime = new Trend('stomp_connect_ms', true);

// 2. 구독
const stompSubscribeTime = new Trend('stomp_subscribe_ms', true);
const subscribeSuccess = new Counter('stomp_subscribe_success');

// 3. 메시지 송수신
const msgSent = new Counter('stomp_messages_sent');
const msgRoundtripTime = new Trend('stomp_message_roundtrip_ms', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

const MAX_VUS = parseInt(__ENV.MAX_VUS || '500');
const MEMBER_OFFSET = 20000;
const MSG_INTERVAL_MS = 5000;
const TOTAL_DURATION_MS = 10 * 60 * 1000;

const ROOM_IDS = Array.from({length: 20}, (_, i) => 9021 + i);

// 5 stages: (MAX_VUS-400) → (MAX_VUS-300) → ... → MAX_VUS, 각 2m
const stages = Array.from({length: 5}, (_, i) => ({
    duration: '2m',
    target: Math.max(1, MAX_VUS - (4 - i) * 100),
}));

export const options = {
    stages,
    thresholds: {
        // 연결
        http_req_failed:            ['rate<0.01'],
        ws_connecting:              ['p(95)<2000'],
        stomp_connect_ms:           ['p(95)<2000'],
        // 구독
        stomp_subscribe_ms:         ['p(95)<2000'],
        stomp_subscribe_success:    ['count>0'],
        // 메시지 송수신
        stomp_messages_sent:        ['count>0'],
        stomp_message_roundtrip_ms: ['p(95)<2000'],
        // 전체
        iteration_duration:         ['p(95)<2000'],
    },
};

export function setup() {
    const tokens = [];
    for (let i = 0; i < MAX_VUS; i++) {
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
        subscribeSentAt: 0,
        subscribed: false,
        pendingMsg: null,  // { sentAt }
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
                state.subscribeSentAt = Date.now();
                socket.send(stomp.subscribe('sub-0', `/topic/rooms/${roomId}`));

            } else if (cmd === 'MESSAGE') {
                const body = stomp.parseBody(raw);

                if (!state.subscribed) {
                    if (body?.type === 'SUBSCRIBE_FAILED') {
                        socket.close();
                        return;
                    }
                    if (body?.type === 'SUBSCRIBED') {
                        stompSubscribeTime.add(Date.now() - state.subscribeSentAt);
                        subscribeSuccess.add(1);
                        state.subscribed = true;

                        socket.setInterval(() => {
                            state.pendingMsg = {sentAt: Date.now()};
                            msgSent.add(1);
                            socket.send(stomp.send('/app/chat.send', {
                                clientMessageId: `k6-ramp-${__VU}-${Date.now()}`,
                                roomId: roomId,
                                type: 'TEXT',
                                content: 'ramping test msg',
                            }));
                        }, MSG_INTERVAL_MS);
                    }
                    return;
                }

                if (body?.event === 'MESSAGE_SENT') {
                    if (state.pendingMsg) {
                        msgRoundtripTime.add(Date.now() - state.pendingMsg.sentAt);
                        state.pendingMsg = null;
                    }
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
