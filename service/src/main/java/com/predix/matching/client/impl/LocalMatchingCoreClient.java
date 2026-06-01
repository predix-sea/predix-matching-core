package com.predix.matching.client.impl;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.engine.InMemoryOrderBook;
import com.predix.matching.engine.MatchingEngine;
import com.predix.matching.engine.OrderBookRegistry;
import com.predix.matching.engine.model.BookOrder;
import com.predix.matching.engine.model.MatchResult;
import com.predix.matching.engine.model.TradeFill;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Delegates to in-process order book registry.
 * Production deployments should use {@link GrpcMatchingCoreClient} when C++ core gRPC is available.
 */
@Component
@org.springframework.context.annotation.Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "predix.matching-core.grpc.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalMatchingCoreClient implements MatchingCoreClient {

    private final OrderBookRegistry registry;

    @Override
    public CoreMatchResult submitOrder(OrderEntity order) {
        MatchResult result = registry.getOrCreate(order.getMarketId(), order.getOutcomeId())
                .match(MatchingEngine.toBookOrder(order));
        if (result.isRejected()) {
            throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_LIQUIDITY, result.getRejectReason());
        }
        return toCoreResult(order.getId(), result);
    }

    @Override
    public boolean cancelOrder(OrderEntity order) {
        var book = registry.get(order.getMarketId(), order.getOutcomeId());
        return book != null && book.removeFromBook(order.getId());
    }

    @Override
    public List<CoreMatchResult.CoreDepthLevel> getDepth(String marketId, String outcomeId, int levels) {
        return registry.getOrCreate(marketId, outcomeId).getDepth(levels).stream()
                .map(d -> CoreMatchResult.CoreDepthLevel.builder()
                        .side(d.side())
                        .price(d.price())
                        .quantity(d.quantity())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders) {
        var book = registry.getOrCreate(marketId, outcomeId);
        for (var o : orders) {
            book.addToBook(BookOrder.builder()
                    .id(o.getId())
                    .userId(o.getUserId())
                    .side(o.getSide())
                    .orderType(o.getOrderType())
                    .price(o.getPrice())
                    .remainingQuantity(o.getRemainingQuantity())
                    .createdAt(java.time.Instant.ofEpochMilli(o.getCreatedAtEpochMs()))
                    .sequence(o.getSequence())
                    .build());
        }
        return orders.size();
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    private CoreMatchResult toCoreResult(java.util.UUID orderId, MatchResult result) {
        List<CoreMatchResult.CoreTradeFill> fills = result.getFills().stream()
                .map(this::toFill)
                .collect(Collectors.toList());
        return CoreMatchResult.builder()
                .orderId(orderId)
                .remainingQuantity(result.getIncomingOrder().getRemainingQuantity())
                .fullyFilled(result.isFullyFilled())
                .rejected(result.isRejected())
                .rejectReason(result.getRejectReason())
                .fills(fills)
                .build();
    }

    private CoreMatchResult.CoreTradeFill toFill(TradeFill f) {
        return CoreMatchResult.CoreTradeFill.builder()
                .makerOrderId(f.getMakerOrderId())
                .makerUserId(f.getMakerUserId())
                .takerOrderId(f.getTakerOrderId())
                .takerUserId(f.getTakerUserId())
                .price(f.getPrice())
                .quantity(f.getQuantity())
                .buyerIsTaker(f.isBuyerIsTaker())
                .build();
    }
}
