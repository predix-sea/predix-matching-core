package com.predix.matching.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingEventMessage {
    private String messageId;
    private String correlationId;
    private String eventType;
    private String refId;
    private Map<String, Object> payload;
    private Instant timestamp;
}
