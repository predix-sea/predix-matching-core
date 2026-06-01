package com.predix.matching.controller.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class TradeResponse {
    UUID id;
    String tradeCode;
    String marketId;
    String outcomeId;
    UUID buyOrderId;
    UUID sellOrderId;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal notional;
    String makerUserId;
    String takerUserId;
    Instant createdAt;
}
