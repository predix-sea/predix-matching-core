package com.predix.matching.engine;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.MatchResult;
import com.predix.matching.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private InMemoryOrderBook book;

    @BeforeEach
    void setUp() {
        OrderBookRegistry registry = new OrderBookRegistry();
        matchingEngine = new MatchingEngine(registry);
        book = registry.getOrCreate("mkt", "yes");
    }

    @Test
    void processOrder_matchesRestingSell() {
        book.addToBook(MatchingEngine.toBookOrder(sellOrder("0.50", "10")));

        MatchResult result = matchingEngine.processOrder(buyOrder("0.55", "5"));

        assertThat(result.getFills()).hasSize(1);
        assertThat(result.isFullyFilled()).isTrue();
    }

    @Test
    void processOrder_marketNoLiquidity_rejected() {
        assertThatThrownBy(() -> matchingEngine.processOrder(marketBuy("10")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveStatus_partialAfterPartialFill() {
        assertThat(MatchingEngine.resolveStatusAfterMatch(
                new BigDecimal("10"), new BigDecimal("4"))).isEqualTo(OrderStatus.PARTIAL);
    }

    private OrderEntity buyOrder(String price, String qty) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .marketId("mkt")
                .outcomeId("yes")
                .userId("buyer")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .remainingQuantity(new BigDecimal(qty))
                .status(OrderStatus.NEW)
                .build();
    }

    private OrderEntity marketBuy(String qty) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .marketId("mkt")
                .outcomeId("yes")
                .userId("buyer")
                .side(OrderSide.BUY)
                .orderType(OrderType.MARKET)
                .quantity(new BigDecimal(qty))
                .remainingQuantity(new BigDecimal(qty))
                .status(OrderStatus.NEW)
                .build();
    }

    private OrderEntity sellOrder(String price, String qty) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .marketId("mkt")
                .outcomeId("yes")
                .userId("seller")
                .side(OrderSide.SELL)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .remainingQuantity(new BigDecimal(qty))
                .status(OrderStatus.NEW)
                .build();
    }
}
