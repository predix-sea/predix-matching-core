package com.predix.matching.engine.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class TradeFill {
    UUID makerOrderId;
    String makerUserId;
    UUID takerOrderId;
    String takerUserId;
    BigDecimal price;
    BigDecimal quantity;
    boolean buyerIsTaker;
}
