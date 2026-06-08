package com.predix.matching.testsupport.inmemory.model;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
public class BookOrder {
    UUID id;
    String userId;
    OrderSide side;
    OrderType orderType;
    BigDecimal price;
    BigDecimal remainingQuantity;
    Instant createdAt;
    long sequence;
}
