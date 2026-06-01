package com.predix.matching.client.grpc;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.grpc.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GrpcDecimalConverter {

    private static final int SCALE = 8;
    private static final BigDecimal MULTIPLIER = BigDecimal.TEN.pow(SCALE);

    private GrpcDecimalConverter() {}

    public static long toRaw(BigDecimal value) {
        if (value == null) {
            return 0L;
        }
        return value.multiply(MULTIPLIER).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal fromRaw(long raw) {
        return BigDecimal.valueOf(raw, SCALE);
    }

    public static Side toGrpcSide(OrderSide side) {
        return side == OrderSide.BUY ? Side.SIDE_BUY : Side.SIDE_SELL;
    }

    public static OrderSide fromGrpcSide(Side side) {
        return side == Side.SIDE_SELL ? OrderSide.SELL : OrderSide.BUY;
    }

    public static com.predix.matching.grpc.OrderType toGrpcOrderType(OrderType orderType) {
        return orderType == OrderType.MARKET
                ? com.predix.matching.grpc.OrderType.ORDER_TYPE_MARKET
                : com.predix.matching.grpc.OrderType.ORDER_TYPE_LIMIT;
    }

    public static OrderType fromGrpcOrderType(com.predix.matching.grpc.OrderType orderType) {
        return orderType == com.predix.matching.grpc.OrderType.ORDER_TYPE_MARKET
                ? OrderType.MARKET
                : OrderType.LIMIT;
    }
}
