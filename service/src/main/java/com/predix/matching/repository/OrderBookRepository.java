package com.predix.matching.repository;

import com.predix.matching.domain.entity.OrderBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderBookRepository extends JpaRepository<OrderBookEntity, Long> {

    Optional<OrderBookEntity> findByMarketIdAndOutcomeId(String marketId, String outcomeId);
}
