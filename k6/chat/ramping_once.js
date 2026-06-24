/**
 * [A] 단일 메시지 전송 부하 테스트 — Ramping 패턴 (수정본)
 *
 * 시나리오: VU 마다 WebSocket 연결 → 구독 ACK 대기 → 메시지 1건 전송 → 연결 종료
 * 목적:      VU 증가에 따라 단일 메시지 처리량과 단발성 레이턴시 변화 측정
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
const msgConfirmed = new Counter('stomp_messages_confirmed');
const msgRoundtripTime = new Trend('stomp_message_roundtrip_ms', true);
const subscribeFailed = new Counter('stomp_subscribe_failed');

// 4. 서버→클라이언트 단방향 지연
const serverToClientMs = new Trend('stomp_server_to_client_ms', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

const MAX_VUS = parseInt(__ENV.MAX_VUS || '500');
const MEMBER_OFFSET = 20000;
const DELAY_BEFORE_SEND_MS = 1000; // 구독 성공 후 메시지 발송까지의 대기 시간 (필요시 조절)
const ITERATION_DURATION_MS = 30_000;

const ROOM_IDS = Array.from({length: 20}, (_, i) => 9021 + i);

const stages = [
    {duration: '2m', target: MAX_VUS},
];

export const options = {
    stages,
    gracefulStop: '30s',
    gracefulRampDown: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        ws_connecting: ['p(95)<2000'],
        stomp_connect_ms: ['p(95)<2000'],
        stomp_subscribe_ms: ['p(95)<2000'],
        stomp_subscribe_success: ['count>0'],
        stomp_messages_sent: ['count>0'],
        stomp_messages_confirmed: ['count>0'],
        stomp_subscribe_failed: ['count<1'],
        stomp_message_roundtrip_ms: ['p(95)<2000'],
        stomp_server_to_client_ms: ['p(95)<1000'],
        iteration_duration: ['p(95)<2000'],
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
        pendingMsgs: new Map(),
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
                        subscribeFailed.add(1);
                        socket.close();
                        return;
                    }
                    if (body?.type === 'SUBSCRIBED') {
                        stompSubscribeTime.add(Date.now() - state.subscribeSentAt);
                        subscribeSuccess.add(1);
                        state.subscribed = true;

                        // 💡 지전체 루프 대신 setTimeout을 사용하여 단 1번만 발송하도록 수정
                        socket.setTimeout(() => {
                            const clientMessageId = `k6-ramp-${__VU}-${Date.now()}`;
                            state.pendingMsgs.set(clientMessageId, Date.now());
                            msgSent.add(1);
                            socket.send(stomp.send('/app/chat.send', {
                                clientMessageId,
                                roomId: roomId,
                                type: 'TEXT',
                                content: 'ramping test single msg',
                            }));
                        }, DELAY_BEFORE_SEND_MS);
                    }
                    return;
                }

                if (body?.event === 'MESSAGE_SENT') {
                    const cid = body?.payload?.clientMessageId;
                    if (cid && state.pendingMsgs.has(cid)) {
                        const now = Date.now();
                        msgConfirmed.add(1);
                        msgRoundtripTime.add(now - state.pendingMsgs.get(cid));
                        state.pendingMsgs.delete(cid);

                        if (body.occurredAt) {
                            serverToClientMs.add(now - new Date(body.occurredAt).getTime());
                        }
                    }
                }

            } else if (cmd === 'ERROR') {
                socket.close();
            }
        });

        socket.on('error', () => socket.close());

        socket.setTimeout(() => socket.close(), ITERATION_DURATION_MS);
    });

    check(res, {'ws handshake 101': (r) => r && r.status === 101});
}
