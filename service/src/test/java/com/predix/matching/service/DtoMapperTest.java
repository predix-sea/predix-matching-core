package com.predix.matching.service;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMapperTest {

    private final DtoMapper mapper = new DtoMapper();

    @Test
    void mapsOrderEntity() {
        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .orderCode("ORD123")
                .marketId("m1")
                .outcomeId("yes")
                .userId("u1")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("0.5"))
                .quantity(new BigDecimal("10"))
                .remainingQuantity(new BigDecimal("10"))
                .status(OrderStatus.NEW)
                .clientOrderId("c1")
                .build();

        assertThat(mapper.toOrderResponse(order).getOrderCode()).isEqualTo("ORD123");
    }
}
