package com.sportsify.scenario;

import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트 전용 서브클래스.
 * ConfirmPaymentRequest는 setter/생성자가 없으므로(API 입력 보호 목적)
 * 프로덕트 코드 수정 없이 ReflectionTestUtils로 필드를 주입한다.
 * 프로덕트 코드에 테스트 생성자가 추가되면 이 클래스를 삭제하면 된다.
 */
class TestConfirmPaymentRequest extends ConfirmPaymentRequest {

    static TestConfirmPaymentRequest of(String tossOrderId, Long amount) {
        TestConfirmPaymentRequest req = new TestConfirmPaymentRequest();
        ReflectionTestUtils.setField(req, "tossOrderId", tossOrderId);
        ReflectionTestUtils.setField(req, "paymentKey", "MOCK_" + tossOrderId);
        ReflectionTestUtils.setField(req, "amount", amount);
        return req;
    }
}
