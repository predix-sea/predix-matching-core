package com.predix.matching.mq;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.enums.EngineEventType;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("!h2")
@RequiredArgsConstructor
public class EventPublisher implements MatchingMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PredixProperties properties;

    public void publishEvent(EngineEventType eventType, String refId, Map<String, Object> payload) {
        MatchingEventMessage message = MatchingEventMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .correlationId(MDC.get("traceId"))
                .eventType(eventType.name())
                .refId(refId)
                .payload(payload)
                .timestamp(Instant.now())
                .build();
        rabbitTemplate.convertAndSend(properties.getMq().getExchange(), "events." + eventType.name().toLowerCase(), message);
    }

    public void publishExecutionTask(ExecutionTaskMessage message) {
        rabbitTemplate.convertAndSend(properties.getMq().getExchange(), "execution.task", message);
    }

    public void publishToDlq(Object message) {
        rabbitTemplate.convertAndSend(properties.getMq().getExchange(), "dlq.failed", message);
    }
}
