import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL || 'https://localhost:8443';

/**
 * payment.completed 단건 발행
 * @param {number} memberId
 * @param {number} iter  — __ITER
 */
export function publishPayment(memberId, iter) {
    const payload = JSON.stringify({ paymentId: iter, memberId, amount: 10000 });
    return http.post(
        `${BASE_URL}/dev/events/publish`,
        JSON.stringify({ stream: 'payment.completed', payload }),
        { headers: { 'Content-Type': 'application/json' } }
    );
}

/**
 * ticket.opened 브로드캐스트 발행
 * @param {number} iter  — __ITER
 */
export function publishBroadcast(iter) {
    const saleStartAt = `2099-01-${String((iter % 28) + 1).padStart(2, '0')}T10:00:00.000Z`;
    const payload = JSON.stringify({ gameId: iter, saleStartAt });
    return http.post(
        `${BASE_URL}/dev/events/publish`,
        JSON.stringify({ stream: 'ticket.opened', payload }),
        { headers: { 'Content-Type': 'application/json' } }
    );
}
