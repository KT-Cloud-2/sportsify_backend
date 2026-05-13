package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationChunkService {

    private final NotificationDispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processChunk(NotificationEvent event, List<Long> memberIds, String payload) {
        if (memberIds.isEmpty()) {
            return false;
        }
        boolean anyFailed = false;
        for (Long memberId : memberIds) {
            if (dispatcher.dispatchToMember(event, memberId, payload)) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }
}
