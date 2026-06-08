package com.predix.matching.engine;

import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({
            "NEW,PARTIAL,true",
            "NEW,FILLED,true",
            "NEW,CANCELLED,true",
            "PARTIAL,FILLED,true",
            "PARTIAL,CANCELLED,true",
            "NEW,PENDING_MATCH,true",
            "PENDING_MATCH,PARTIAL,true",
            "PENDING_MATCH,FILLED,true",
            "PENDING_MATCH,CANCELLED,true",
            "PENDING_MATCH,PENDING_CANCEL,true",
            "NEW,PENDING_CANCEL,true",
            "PARTIAL,PENDING_CANCEL,true",
            "PENDING_CANCEL,CANCELLED,true",
            "PARTIAL,PENDING_MATCH,true",
            "FILLED,CANCELLED,false",
            "CANCELLED,NEW,false",
            "PARTIAL,NEW,false"
    })
    void statusTransitions(OrderStatus from, OrderStatus to, boolean allowed) {
        if (allowed) {
            OrderStatusTransition.validateTransition(from, to);
        } else {
            assertThatThrownBy(() -> OrderStatusTransition.validateTransition(from, to))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void afterFill_filledWhenZeroRemaining() {
        assertThat(OrderStatusTransition.afterFill(BigDecimal.ZERO)).isEqualTo(OrderStatus.FILLED);
        assertThat(OrderStatusTransition.afterFill(new BigDecimal("5"))).isEqualTo(OrderStatus.PARTIAL);
    }
}
