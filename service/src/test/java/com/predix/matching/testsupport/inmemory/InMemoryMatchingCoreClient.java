package com.predix.matching.testsupport.inmemory;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.engine.model.TradeFill;
import com.predix.matching.testsupport.inmemory.model.BookOrder;
import com.predix.matching.testsupport.inmemory.model.MatchResult;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** H2 integration tests only; production uses {@link com.predix.matching.client.impl.GrpcMatchingCoreClient}. */
@Component
@Profile({"h2", "test"})
@Primary
public class InMemoryMatchingCoreClient implements MatchingCoreClient {

    private final OrderBookRegistry registry = new OrderBookRegistry();

    @Override
    public CoreMatchResult submitOrder(OrderEntity order) {
        MatchResult result = registry.getOrCreate(order.getMarketId(), order.getOutcomeId())
                .match(toBookOrder(order));
        return toCoreResult(order.getId(), result);
    }

    @Override
    public boolean cancelOrder(OrderEntity order) {
        InMemoryOrderBook book = registry.get(order.getMarketId(), order.getOutcomeId());
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
    public int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders,
                          boolean replaceExisting) {
        if (replaceExisting) {
            registry.reset(marketId, outcomeId);
        }
        InMemoryOrderBook book = registry.getOrCreate(marketId, outcomeId);
        for (var o : orders) {
            book.addToBook(BookOrder.builder()
                    .id(o.getId())
                    .userId(o.getUserId())
                    .side(o.getSide())
                    .orderType(o.getOrderType())
                    .price(o.getPrice())
                    .remainingQuantity(o.getRemainingQuantity())
                    .createdAt(Instant.ofEpochMilli(o.getCreatedAtEpochMs()))
                    .sequence(o.getSequence())
                    .build());
        }
        return orders.size();
    }

    @Override
    public boolean resetBook(String marketId, String outcomeId) {
        registry.reset(marketId, outcomeId);
        return true;
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    private static BookOrder toBookOrder(OrderEntity order) {
        return BookOrder.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .price(order.getPrice())
                .remainingQuantity(order.getRemainingQuantity())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now())
                .sequence(0)
                .build();
    }

    private CoreMatchResult toCoreResult(java.util.UUID orderId, MatchResult result) {
        return CoreMatchResult.builder()
                .orderId(orderId)
                .remainingQuantity(result.getIncomingOrder().getRemainingQuantity())
                .fullyFilled(result.isFullyFilled())
                .rejected(result.isRejected())
                .rejectReason(result.getRejectReason())
                .fills(result.getFills().stream().map(InMemoryMatchingCoreClient::toFill).collect(Collectors.toList()))
                .build();
    }

    private static CoreMatchResult.CoreTradeFill toFill(TradeFill f) {
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
