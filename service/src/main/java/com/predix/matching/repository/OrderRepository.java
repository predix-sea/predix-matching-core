package com.predix.matching.repository;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByUserIdAndClientOrderId(String userId, String clientOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE (:marketId IS NULL OR o.marketId = :marketId)
              AND (:userId IS NULL OR o.userId = :userId)
              AND (:status IS NULL OR o.status = :status)
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findByFilters(
            @Param("marketId") String marketId,
            @Param("userId") String userId,
            @Param("status") OrderStatus status,
            Pageable pageable);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.marketId = :marketId AND o.outcomeId = :outcomeId
              AND o.status IN ('NEW', 'PARTIAL')
            ORDER BY o.createdAt ASC
            """)
    List<OrderEntity> findOpenOrders(String marketId, String outcomeId);

    List<OrderEntity> findByMarketIdAndOutcomeIdAndStatusIn(
            String marketId, String outcomeId, List<OrderStatus> statuses);
}
