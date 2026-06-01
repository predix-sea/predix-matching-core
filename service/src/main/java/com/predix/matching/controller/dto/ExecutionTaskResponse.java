package com.predix.matching.controller.dto;

import com.predix.matching.domain.enums.ExecutionTaskStatus;
import com.predix.matching.domain.enums.ExecutionTaskType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class ExecutionTaskResponse {
    UUID id;
    String taskCode;
    String marketId;
    ExecutionTaskType taskType;
    Map<String, Object> payload;
    ExecutionTaskStatus status;
    int retryCount;
    Instant nextRetryAt;
    String idempotencyKey;
    Instant createdAt;
    Instant updatedAt;
}
