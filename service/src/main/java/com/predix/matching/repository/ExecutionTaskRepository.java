package com.predix.matching.repository;

import com.predix.matching.domain.entity.ExecutionTaskEntity;
import com.predix.matching.domain.enums.ExecutionTaskStatus;
import com.predix.matching.domain.enums.ExecutionTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionTaskRepository extends JpaRepository<ExecutionTaskEntity, UUID> {

    Optional<ExecutionTaskEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ExecutionTaskEntity t WHERE t.id = :id")
    Optional<ExecutionTaskEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT t FROM ExecutionTaskEntity t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:taskType IS NULL OR t.taskType = :taskType)
            ORDER BY t.createdAt DESC
            """)
    Page<ExecutionTaskEntity> findByFilters(
            @Param("status") ExecutionTaskStatus status,
            @Param("taskType") ExecutionTaskType taskType,
            Pageable pageable);

    @Query("""
            SELECT t FROM ExecutionTaskEntity t
            WHERE t.status IN ('RETRYING', 'FAILED')
              AND t.nextRetryAt IS NOT NULL
              AND t.nextRetryAt <= :now
              AND t.retryCount < :maxRetries
            """)
    List<ExecutionTaskEntity> findDueForRetry(@Param("now") Instant now, @Param("maxRetries") int maxRetries);
}
