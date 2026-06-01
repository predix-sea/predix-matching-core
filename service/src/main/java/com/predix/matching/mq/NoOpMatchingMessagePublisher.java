package com.predix.matching.mq;

import com.predix.matching.domain.enums.EngineEventType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("h2")
public class NoOpMatchingMessagePublisher implements MatchingMessagePublisher {

    @Override
    public void publishEvent(EngineEventType eventType, String refId, Map<String, Object> payload) {
    }

    @Override
    public void publishExecutionTask(ExecutionTaskMessage message) {
    }

    @Override
    public void publishToDlq(Object message) {
    }
}
