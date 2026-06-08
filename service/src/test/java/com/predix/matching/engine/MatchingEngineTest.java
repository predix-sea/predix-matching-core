package com.predix.matching.engine;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.engine.model.TradeFill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    @Test
    void resolveStatusAfterMatch() {
        assertThat(MatchingEngine.resolveStatusAfterMatch(
                new BigDecimal("10"), new BigDecimal("0"))).isEqualTo(OrderStatus.FILLED);
        assertThat(MatchingEngine.resolveStatusAfterMatch(
                new BigDecimal("10"), new BigDecimal("4"))).isEqualTo(OrderStatus.PARTIAL);
        assertThat(MatchingEngine.resolveStatusAfterMatch(
                new BigDecimal("10"), new BigDecimal("10"))).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void applyFillToMaker_updatesRemainingAndStatus() {
        OrderEntity maker = OrderEntity.builder()
                .id(UUID.randomUUID())
                .remainingQuantity(new BigDecimal("10"))
                .status(OrderStatus.NEW)
                .build();
        MatchingEngine.applyFillToMaker(maker, TradeFill.builder()
                .quantity(new BigDecimal("3"))
                .build());
        assertThat(maker.getRemainingQuantity()).isEqualByComparingTo("7");
        assertThat(maker.getStatus()).isEqualTo(OrderStatus.PARTIAL);
    }
}
