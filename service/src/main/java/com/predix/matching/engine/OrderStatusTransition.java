package com.predix.matching.engine;

import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;

import java.math.BigDecimal;

public final class OrderStatusTransition {

    private OrderStatusTransition() {
    }

    public static OrderStatus afterFill(BigDecimal remaining) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return OrderStatus.FILLED;
        }
        return OrderStatus.PARTIAL;
    }

    public static void validateTransition(OrderStatus from, OrderStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_TRANSITION,
                    "Cannot transition from " + from + " to " + to);
        }
    }

    public static OrderStatus applyTransition(OrderStatus current, OrderStatus target) {
        validateTransition(current, target);
        return target;
    }
}
