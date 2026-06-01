package com.predix.matching.mq;

import com.predix.matching.client.CtfExecutionGateway;
import com.predix.matching.client.dto.CtfSubmitResult;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.ExecutionTaskEntity;
import com.predix.matching.domain.enums.ExecutionTaskStatus;
import com.predix.matching.domain.enums.ExecutionTaskType;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.idempotency.IdempotencyService;
import com.predix.matching.repository.ExecutionTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@Profile("!h2")
@RequiredArgsConstructor
public class ExecutionTaskConsumer {

    private static final String CONSUME_PREFIX = "mq:execution:consumed:";

    private final ExecutionTaskRepository taskRepository;
    private final CtfExecutionGateway ctfGateway;
    private final EventPublisher eventPublisher;
    private final PredixProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final IdempotencyService idempotencyService;

    @RabbitListener(queues = "${predix.mq.execution-queue}")
    @Transactional
    public void consume(ExecutionTaskMessage message) {
        String consumeKey = CONSUME_PREFIX + message.getMessageId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(consumeKey, "1", Duration.ofDays(7));
        if (Boolean.FALSE.equals(acquired)) {
            log.info("Skipping duplicate execution message messageId={}", message.getMessageId());
            return;
        }

        ExecutionTaskEntity task = taskRepository.findById(message.getTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_TASK_NOT_FOUND));

        if (task.getStatus() == ExecutionTaskStatus.SUCCEEDED || task.getStatus() == ExecutionTaskStatus.DEAD) {
            return;
        }

        task.setStatus(ExecutionTaskStatus.RUNNING);
        taskRepository.save(task);

        try {
            CtfSubmitResult result = switch (ExecutionTaskType.valueOf(message.getTaskType())) {
                case CTF_TRADE_SUBMIT -> ctfGateway.submitTrade(message.getPayload());
                case CTF_CANCEL -> ctfGateway.cancelOrderOnChain(message.getPayload());
                case CTF_RETRY -> ctfGateway.submitTrade(message.getPayload());
            };
            task.setStatus(ExecutionTaskStatus.SUCCEEDED);
            task.getPayload().put("txHash", result.getTxHash());
            task.getPayload().put("ctfStatus", result.getStatus());
            taskRepository.save(task);
            log.info("Execution task succeeded taskId={} txHash={}", task.getId(), result.getTxHash());
        } catch (Exception e) {
            handleFailure(task, message, e);
        }
    }

    private void handleFailure(ExecutionTaskEntity task, ExecutionTaskMessage message, Exception e) {
        int nextRetry = task.getRetryCount() + 1;
        task.setRetryCount(nextRetry);
        int maxRetries = properties.getMatching().getMaxRetryCount();

        if (nextRetry >= maxRetries) {
            task.setStatus(ExecutionTaskStatus.DEAD);
            taskRepository.save(task);
            eventPublisher.publishToDlq(message);
            log.error("Execution task dead taskId={} retries={}", task.getId(), nextRetry, e);
            throw new BusinessException(ErrorCode.EXECUTION_TASK_RETRY_EXCEEDED);
        }

        long delayMs = properties.getMatching().getRetryBaseDelayMs() * (1L << (nextRetry - 1));
        task.setStatus(ExecutionTaskStatus.RETRYING);
        task.setNextRetryAt(Instant.now().plusMillis(delayMs));
        taskRepository.save(task);
        log.warn("Execution task retry scheduled taskId={} retry={} delayMs={}", task.getId(), nextRetry, delayMs, e);
        throw new RuntimeException("Retryable failure", e);
    }
}
