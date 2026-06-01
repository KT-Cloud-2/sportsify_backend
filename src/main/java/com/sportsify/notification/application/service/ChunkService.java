package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkService {

    private final Dispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processChunk(NotificationEvent event, List<Long> memberIds, String payload) {
        if (memberIds.isEmpty()) {
            return false;
        }
        boolean anyFailed = false;
        for (Long memberId : memberIds) {
            if (dispatcher.toMember(event, memberId, payload)) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }
}
