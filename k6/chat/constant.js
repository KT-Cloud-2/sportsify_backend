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
 */

import ws from 'k6/ws';
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';
import {stomp} from './lib/stomp.js';

const stompConnectTime = new Trend('stomp_connect_ms', true);
const readSent = new Counter('stomp_read_sent');
const readRoundtripTime = new Trend('stomp_read_roundtrip_ms', true);
const readReceiptTimeout = new Counter('stomp_read_receipt_timeout');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

const VU_BUFFER = 520;
const MEMBER_OFFSET = 30000;
const MSG_INTERVAL_MS = 5000;
const TOTAL_DURATION_MS = 11 * 60 * 1000;

const ROOM_IDS = Array.from({length: 20}, (_, i) => 9001 + i);

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
        stomp_read_sent: ['count>0'],
        stomp_read_roundtrip_ms: ['p(95)<8000'],
        stomp_read_receipt_timeout: ['count<10'],
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
        pendingRead: null,   // { messageId, sentAt }
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

                // 구독 ACK 대기
                if (!state.subscribed) {
                    if (body?.type === 'SUBSCRIBE_FAILED') {
                        socket.close();
                        return;
                    }
                    if (body?.type === 'SUBSCRIBED') {
                        state.subscribed = true;
                        socket.setInterval(() => {
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

                    state.pendingRead = {messageId, sentAt: Date.now()};
                    socket.send(stomp.send('/app/chat.read', {
                        roomId: roomId,
                        lastReadMessageId: messageId,
                    }));
                    readSent.add(1);

                    // flush-interval(5s) 이후에도 READ_RECEIPT가 없으면 유실로 카운팅
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
