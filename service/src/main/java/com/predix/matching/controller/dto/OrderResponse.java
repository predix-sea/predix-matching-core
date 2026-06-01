package com.predix.matching.controller.dto;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class OrderResponse {
    UUID id;
    String orderCode;
    String marketId;
    String outcomeId;
    String userId;
    OrderSide side;
    OrderType orderType;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal remainingQuantity;
    OrderStatus status;
    String clientOrderId;
    Instant createdAt;
    Instant updatedAt;
    List<TradeResponse> trades;
}
