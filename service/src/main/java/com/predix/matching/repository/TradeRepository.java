package com.predix.matching.repository;

import com.predix.matching.domain.entity.TradeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {

    @Query("""
            SELECT t FROM TradeEntity t
            WHERE (:marketId IS NULL OR t.marketId = :marketId)
              AND (:outcomeId IS NULL OR t.outcomeId = :outcomeId)
            ORDER BY t.createdAt DESC
            """)
    Page<TradeEntity> findByFilters(
            @Param("marketId") String marketId,
            @Param("outcomeId") String outcomeId,
            Pageable pageable);
}
