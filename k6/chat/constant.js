/**
 * [B] 읽음 처리 부하 테스트 — Constant 패턴
 *
 * 시나리오: VU 마다 WebSocket 연결 → 구독 ACK 대기 → duration 내내 구독 유지 →
 *           주기적으로 메시지 전송 → messageId 획득 → chat.read 전송 (반복)
 * 목적:     500 VU 고부하 상태에서 읽음 처리(advisory lock + DB 업데이트)의
 *           처리량과 레이턴시를 측정
 *
 * 실행:
 *   k6 run k6/chat/constant.js
 *   k6 run -e BASE_URL=http://staging.example.com k6/chat/constant.js
 *
 * 사전 조건:
 *   - k6/chat/seed.sql 을 실행해 멤버·방·멤버십 데이터를 미리 적재한다
 *     예) ./k6/chat/run.sh constant
 *   - /dev/token?memberId={id} 엔드포인트가 활성화되어 있어야 한다 (local 프로파일)
 *
 * 측정 지표 (단계별):
 *   1. 연결       : ws_connecting, stomp_connect_ms
 *   2. 구독       : stomp_subscribe_ms, stomp_subscribe_success
 *   3. 메시지 송수신: stomp_message_sent, stomp_message_roundtrip_ms
 *   4. 읽음 처리  : stomp_read_sent, stomp_read_roundtrip_ms, stomp_read_receipt_timeout
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
const messageSent = new Counter('stomp_message_sent');
const messageRoundtripTime = new Trend('stomp_message_roundtrip_ms', true);

// 4. 읽음 처리
const readSent = new Counter('stomp_read_sent');
const readRoundtripTime = new Trend('stomp_read_roundtrip_ms', true);
const readReceiptTimeout = new Counter('stomp_read_receipt_timeout');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

const MAX_VUS = parseInt(__ENV.MAX_VUS || '500');
const MEMBER_OFFSET = 30000;
const MSG_INTERVAL_MS = 5000;
const TOTAL_DURATION_MS = 10 * 60 * 1000;

const ROOM_IDS = Array.from({length: 20}, (_, i) => 9001 + i);

// 5 stages: (MAX_VUS-400) → (MAX_VUS-300) → ... → MAX_VUS, 각 2m
const stages = Array.from({length: 5}, (_, i) => ({
    duration: '2m',
    target: Math.max(1, MAX_VUS - (4 - i) * 100),
}));

export const options = {
    stages,
    thresholds: {
        // 연결
        http_req_failed:          ['rate<0.01'],
        ws_connecting:            ['p(95)<2000'],
        stomp_connect_ms:         ['p(95)<2000'],
        // 구독
        stomp_subscribe_ms:       ['p(95)<2000'],
        stomp_subscribe_success:  ['count>0'],
        // 메시지 송수신
        stomp_message_sent:           ['count>0'],
        stomp_message_roundtrip_ms:   ['p(95)<2000'],
        // 읽음 처리
        stomp_read_sent:              ['count>0'],
        stomp_read_roundtrip_ms:      ['p(95)<3000'],
        stomp_read_receipt_timeout:   ['count<10'],
        // 전체
        iteration_duration:       ['p(95)<2000'],
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
        pendingMsg: null,   // { sentAt }
        pendingRead: null,  // { messageId, sentAt }
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
                            messageSent.add(1);
                            socket.send(stomp.send('/app/chat.send', {
                                clientMessageId: `k6-const-${__VU}-${Date.now()}`,
                                roomId: roomId,
                                type: 'TEXT',
                                content: 'read receipt load test',
                            }));
                        }, MSG_INTERVAL_MS);
                    }
                    return;
                }

                if (body?.event === 'MESSAGE_SENT') {
                    const messageId = body?.payload?.messageId;
                    if (!messageId) return;

                    if (state.pendingMsg) {
                        messageRoundtripTime.add(Date.now() - state.pendingMsg.sentAt);
                        state.pendingMsg = null;
                    }

                    state.pendingRead = {messageId, sentAt: Date.now()};
                    socket.send(stomp.send('/app/chat.read', {
                        roomId: roomId,
                        lastReadMessageId: messageId,
                    }));
                    readSent.add(1);

                    socket.setTimeout(() => {
                        if (state.pendingRead) {
                            readReceiptTimeout.add(1);
                            state.pendingRead = null;
                        }
                    }, 8000);
                }

                if (body?.event === 'READ_RECEIPT') {
                    if (
                        state.pendingRead &&
                        body.payload.lastReadMessageId === state.pendingRead.messageId
                    ) {
                        readRoundtripTime.add(Date.now() - state.pendingRead.sentAt);
                        state.pendingRead = null;
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
