package com.predix.matching.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predix.matching.domain.entity.IdempotencyRecordEntity;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.repository.IdempotencyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String REDIS_PREFIX = "idempotency:";

    private final IdempotencyRecordRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              ObjectMapper objectMapper,
                              @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> getCachedResponse(String key) {
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        return repository.findByIdempotencyKey(key)
                .filter(r -> r.getExpiresAt().isAfter(Instant.now()))
                .map(IdempotencyRecordEntity::getResponseBody);
    }

    @Transactional
    public void saveResponse(String key, String resourceType, String resourceId, Object response) {
        String body;
        try {
            body = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
        Instant expiresAt = Instant.now().plus(TTL);
        IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
                .idempotencyKey(key)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .responseBody(body)
                .expiresAt(expiresAt)
                .build();
        repository.save(record);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(REDIS_PREFIX + key, body, TTL);
        }
    }

    public String buildOrderKey(String userId, String clientOrderId) {
        return "order:" + userId + ":" + clientOrderId;
    }

    public String buildExecutionKey(String key) {
        return "execution:" + key;
    }

    public void assertNoConflict(String key, String expectedResourceId, String actualResourceId) {
        if (!expectedResourceId.equals(actualResourceId)) {
            throw new BusinessException(ErrorCode.ORDER_IDEMPOTENCY_CONFLICT);
        }
    }
}
