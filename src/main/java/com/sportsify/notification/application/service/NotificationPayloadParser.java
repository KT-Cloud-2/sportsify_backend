package com.sportsify.notification.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class NotificationPayloadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Long extractMemberId(String payload, String eventTypeName) {
        try {
            JsonNode node = MAPPER.readTree(payload);
            JsonNode memberIdNode = node.get("memberId");
            if (memberIdNode == null || memberIdNode.isNull() || !memberIdNode.isIntegralNumber()) {
                throw new IllegalArgumentException("invalid memberId in " + eventTypeName + " payload");
            }
            return memberIdNode.longValue();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid memberId in " + eventTypeName + " payload", e);
        }
    }
}
