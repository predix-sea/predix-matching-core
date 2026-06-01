package com.predix.matching.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTaskMessage {
    private String messageId;
    private String correlationId;
    private UUID taskId;
    private String taskType;
    private String marketId;
    private Map<String, Object> payload;
    private String idempotencyKey;
    private int retryCount;
}
