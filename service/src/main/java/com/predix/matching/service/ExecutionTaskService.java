package com.predix.matching.service;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.controller.dto.ExecutionTaskResponse;
import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.domain.entity.ExecutionTaskEntity;
import com.predix.matching.domain.enums.ExecutionTaskStatus;
import com.predix.matching.domain.enums.ExecutionTaskType;
import com.predix.matching.domain.enums.EngineEventType;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.mq.MatchingMessagePublisher;
import com.predix.matching.mq.ExecutionTaskMessage;
import com.predix.matching.repository.ExecutionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionTaskService {

    private final ExecutionTaskRepository taskRepository;
    private final CodeGenerator codeGenerator;
    private final MatchingMessagePublisher eventPublisher;
    private final EngineEventService engineEventService;
    private final DtoMapper dtoMapper;
    private final PredixProperties properties;

    @Transactional
    public ExecutionTaskEntity createTradeSubmitTask(String marketId, UUID tradeId, String tradeCode,
                                                     Map<String, Object> tradePayload) {
        String idempotencyKey = "ctf-trade:" + tradeId;
        return taskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> createTask(marketId, ExecutionTaskType.CTF_TRADE_SUBMIT, idempotencyKey, tradePayload));
    }

    @Transactional
    public ExecutionTaskEntity createCancelTask(String marketId, UUID orderId, Map<String, Object> payload) {
        String idempotencyKey = "ctf-cancel:" + orderId;
        return taskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> createTask(marketId, ExecutionTaskType.CTF_CANCEL, idempotencyKey, payload));
    }

    private ExecutionTaskEntity createTask(String marketId, ExecutionTaskType type,
                                           String idempotencyKey, Map<String, Object> payload) {
        ExecutionTaskEntity task = ExecutionTaskEntity.builder()
                .taskCode(codeGenerator.taskCode())
                .marketId(marketId)
                .taskType(type)
                .payload(new HashMap<>(payload))
                .status(ExecutionTaskStatus.PENDING)
                .retryCount(0)
                .idempotencyKey(idempotencyKey)
                .build();
        task = taskRepository.save(task);

        Map<String, Object> eventPayload = Map.of(
                "taskId", task.getId().toString(),
                "taskType", type.name(),
                "marketId", marketId
        );
        engineEventService.recordAndPublish(EngineEventType.EXECUTION_TASK_CREATED, task.getId().toString(), eventPayload);

        ExecutionTaskMessage message = ExecutionTaskMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .correlationId(task.getId().toString())
                .taskId(task.getId())
                .taskType(type.name())
                .marketId(marketId)
                .payload(task.getPayload())
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .build();
        eventPublisher.publishExecutionTask(message);
        return task;
    }

    @Transactional
    public ExecutionTaskResponse retry(UUID taskId) {
        ExecutionTaskEntity task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_TASK_NOT_FOUND));

        if (task.getRetryCount() >= properties.getMatching().getMaxRetryCount()) {
            throw new BusinessException(ErrorCode.EXECUTION_TASK_RETRY_EXCEEDED);
        }

        task.setStatus(ExecutionTaskStatus.PENDING);
        task.setNextRetryAt(null);
        taskRepository.save(task);

        ExecutionTaskMessage message = ExecutionTaskMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .correlationId(task.getId().toString())
                .taskId(task.getId())
                .taskType(task.getTaskType().name())
                .marketId(task.getMarketId())
                .payload(task.getPayload())
                .idempotencyKey(task.getIdempotencyKey())
                .retryCount(task.getRetryCount())
                .build();
        eventPublisher.publishExecutionTask(message);
        return dtoMapper.toTaskResponse(task);
    }

    public ExecutionTaskResponse getById(UUID id) {
        ExecutionTaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_TASK_NOT_FOUND));
        return dtoMapper.toTaskResponse(task);
    }

    public PageResponse<ExecutionTaskResponse> list(ExecutionTaskStatus status, ExecutionTaskType taskType,
                                                    int page, int size) {
        Page<ExecutionTaskEntity> result = taskRepository.findByFilters(status, taskType, PageRequest.of(page, size));
        return dtoMapper.toPage(result.map(dtoMapper::toTaskResponse));
    }

    @Transactional
    public void processDueRetries() {
        var due = taskRepository.findDueForRetry(Instant.now(), properties.getMatching().getMaxRetryCount());
        for (ExecutionTaskEntity task : due) {
            retry(task.getId());
        }
    }
}
