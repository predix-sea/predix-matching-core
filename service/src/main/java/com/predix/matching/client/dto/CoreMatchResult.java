package com.predix.matching.client.dto;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class CoreMatchResult {
    UUID orderId;
    BigDecimal remainingQuantity;
    boolean fullyFilled;
    boolean rejected;
    String rejectReason;
    List<CoreTradeFill> fills;

    @Value
    @Builder
    public static class CoreTradeFill {
        UUID makerOrderId;
        String makerUserId;
        UUID takerOrderId;
        String takerUserId;
        BigDecimal price;
        BigDecimal quantity;
        boolean buyerIsTaker;
    }

    @Value
    @Builder
    public static class CoreDepthLevel {
        OrderSide side;
        BigDecimal price;
        BigDecimal quantity;
    }

    @Value
    @Builder
    public static class CoreBookOrder {
        UUID id;
        String userId;
        OrderSide side;
        OrderType orderType;
        BigDecimal price;
        BigDecimal remainingQuantity;
        long createdAtEpochMs;
        long sequence;
    }
}
