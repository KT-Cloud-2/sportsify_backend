package com.sportsify.notification.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatMentionPayloadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Long extractMemberId(String payload) {
        try {
            JsonNode node = MAPPER.readTree(payload);
            JsonNode memberIdNode = node.get("memberId");
            if (memberIdNode == null || memberIdNode.isNull()) {
                throw new IllegalArgumentException("CHAT_MENTION payload에 memberId 없음");
            }
            return memberIdNode.asLong();
        } catch (Exception e) {
            throw new IllegalArgumentException("CHAT_MENTION payload 파싱 실패: " + payload, e);
        }
    }
}
