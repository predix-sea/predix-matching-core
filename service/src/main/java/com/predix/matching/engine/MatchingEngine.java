package com.predix.matching.engine;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.engine.model.TradeFill;

import java.math.BigDecimal;

/**
 * Order lifecycle helpers after the C++ matching core returns fill results.
 */
public final class MatchingEngine {

    private MatchingEngine() {
    }

    public static OrderStatus resolveStatusAfterMatch(BigDecimal originalQty, BigDecimal remaining) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return OrderStatus.FILLED;
        }
        if (remaining.compareTo(originalQty) < 0) {
            return OrderStatus.PARTIAL;
        }
        return OrderStatus.NEW;
    }

    public static void applyFillToMaker(OrderEntity maker, TradeFill fill) {
        BigDecimal newRemaining = maker.getRemainingQuantity().subtract(fill.getQuantity());
        maker.setRemainingQuantity(newRemaining.max(BigDecimal.ZERO));
        maker.setStatus(OrderStatusTransition.afterFill(maker.getRemainingQuantity()));
    }
}
