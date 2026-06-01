package com.predix.matching.mq;

import com.predix.matching.domain.enums.EngineEventType;

import java.util.Map;

public interface MatchingMessagePublisher {

    void publishEvent(EngineEventType eventType, String refId, Map<String, Object> payload);

    void publishExecutionTask(ExecutionTaskMessage message);

    void publishToDlq(Object message);
}
