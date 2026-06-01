package com.predix.matching.service;

import com.predix.matching.domain.entity.EngineEventEntity;
import com.predix.matching.domain.enums.EngineEventType;
import com.predix.matching.mq.MatchingMessagePublisher;
import com.predix.matching.repository.EngineEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EngineEventService {

    private final EngineEventRepository eventRepository;
    private final MatchingMessagePublisher eventPublisher;

    @Transactional
    public void recordAndPublish(EngineEventType type, String refId, Map<String, Object> payload) {
        eventRepository.save(EngineEventEntity.builder()
                .eventType(type.name())
                .refId(refId)
                .payload(payload)
                .build());
        eventPublisher.publishEvent(type, refId, payload);
    }
}
