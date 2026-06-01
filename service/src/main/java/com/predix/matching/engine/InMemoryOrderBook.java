package com.predix.matching.engine;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.BookOrder;
import com.predix.matching.engine.model.MatchResult;
import com.predix.matching.engine.model.TradeFill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory order book with price-time priority.
 * Bids: highest price first; Asks: lowest price first; FIFO within same price.
 */
public class InMemoryOrderBook {

    private final String marketId;
    private final String outcomeId;
    private final TreeMap<BigDecimal, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, PriceLevel> asks = new TreeMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final Map<UUID, BookOrder> orderIndex = new ConcurrentHashMap<>();

    public InMemoryOrderBook(String marketId, String outcomeId) {
        this.marketId = marketId;
        this.outcomeId = outcomeId;
    }

    public String getMarketId() {
        return marketId;
    }

    public String getOutcomeId() {
        return outcomeId;
    }

    public MatchResult match(BookOrder incoming) {
        List<TradeFill> fills = new ArrayList<>();
        BigDecimal remaining = incoming.getRemainingQuantity();

        TreeMap<BigDecimal, PriceLevel> opposite = incoming.getSide() == OrderSide.BUY ? asks : bids;

        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, PriceLevel> best = opposite.firstEntry();
            BigDecimal bestPrice = best.getKey();

            if (!canMatchPrice(incoming, bestPrice)) {
                break;
            }

            PriceLevel level = best.getValue();
            while (remaining.compareTo(BigDecimal.ZERO) > 0 && !level.isEmpty()) {
                BookOrder maker = level.peek();
                if (maker == null) {
                    break;
                }

                BigDecimal fillQty = remaining.min(maker.getRemainingQuantity());
                BigDecimal tradePrice = maker.getPrice();

                boolean buyerIsTaker = incoming.getSide() == OrderSide.BUY;
                UUID buyOrderId = buyerIsTaker ? incoming.getId() : maker.getId();
                UUID sellOrderId = buyerIsTaker ? maker.getId() : incoming.getId();
                String buyUser = buyerIsTaker ? incoming.getUserId() : maker.getUserId();
                String sellUser = buyerIsTaker ? maker.getUserId() : incoming.getUserId();

                fills.add(TradeFill.builder()
                        .makerOrderId(maker.getId())
                        .makerUserId(maker.getUserId())
                        .takerOrderId(incoming.getId())
                        .takerUserId(incoming.getUserId())
                        .price(tradePrice)
                        .quantity(fillQty)
                        .buyerIsTaker(buyerIsTaker)
                        .build());

                remaining = remaining.subtract(fillQty);
                BigDecimal makerRemaining = maker.getRemainingQuantity().subtract(fillQty);

                if (makerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    level.poll();
                    orderIndex.remove(maker.getId());
                } else {
                    BookOrder updatedMaker = BookOrder.builder()
                            .id(maker.getId())
                            .userId(maker.getUserId())
                            .side(maker.getSide())
                            .orderType(maker.getOrderType())
                            .price(maker.getPrice())
                            .remainingQuantity(makerRemaining)
                            .createdAt(maker.getCreatedAt())
                            .sequence(maker.getSequence())
                            .build();
                    level.poll();
                    level.requeueFront(updatedMaker);
                    orderIndex.put(updatedMaker.getId(), updatedMaker);
                }
            }

            if (level.isEmpty()) {
                opposite.remove(bestPrice);
            }
        }

        BookOrder updatedIncoming = BookOrder.builder()
                .id(incoming.getId())
                .userId(incoming.getUserId())
                .side(incoming.getSide())
                .orderType(incoming.getOrderType())
                .price(incoming.getPrice())
                .remainingQuantity(remaining)
                .createdAt(incoming.getCreatedAt())
                .sequence(incoming.getSequence())
                .build();

        boolean fullyFilled = remaining.compareTo(BigDecimal.ZERO) <= 0;
        boolean rejected = false;
        String rejectReason = null;

        if (!fullyFilled) {
            if (incoming.getOrderType() == OrderType.MARKET) {
                if (fills.isEmpty()) {
                    rejected = true;
                    rejectReason = "No liquidity for market order";
                }
            } else if (incoming.getOrderType() == OrderType.LIMIT) {
                addToBook(updatedIncoming);
            }
        }

        return MatchResult.builder()
                .incomingOrder(updatedIncoming)
                .fills(fills)
                .fullyFilled(fullyFilled)
                .rejected(rejected)
                .rejectReason(rejectReason)
                .build();
    }

    private boolean canMatchPrice(BookOrder incoming, BigDecimal oppositePrice) {
        if (incoming.getOrderType() == OrderType.MARKET) {
            return true;
        }
        if (incoming.getSide() == OrderSide.BUY) {
            return incoming.getPrice().compareTo(oppositePrice) >= 0;
        }
        return incoming.getPrice().compareTo(oppositePrice) <= 0;
    }

    public void addToBook(BookOrder order) {
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TreeMap<BigDecimal, PriceLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
        PriceLevel level = side.computeIfAbsent(order.getPrice(), PriceLevel::new);
        BookOrder withSeq = BookOrder.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .price(order.getPrice())
                .remainingQuantity(order.getRemainingQuantity())
                .createdAt(order.getCreatedAt())
                .sequence(sequence.incrementAndGet())
                .build();
        level.add(withSeq);
        orderIndex.put(withSeq.getId(), withSeq);
    }

    public boolean removeFromBook(UUID orderId) {
        BookOrder order = orderIndex.remove(orderId);
        if (order == null) {
            return false;
        }
        TreeMap<BigDecimal, PriceLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
        PriceLevel level = side.get(order.getPrice());
        if (level == null) {
            return false;
        }
        Deque<BookOrder> temp = new ArrayDeque<>();
        BookOrder found = null;
        while (!level.isEmpty()) {
            BookOrder o = level.poll();
            if (o.getId().equals(orderId)) {
                found = o;
                break;
            }
            temp.addLast(o);
        }
        temp.forEach(level::add);
        if (level.isEmpty()) {
            side.remove(order.getPrice());
        }
        return found != null;
    }

    public List<DepthLevel> getDepth(int levels) {
        List<DepthLevel> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<BigDecimal, PriceLevel> e : bids.entrySet()) {
            if (count++ >= levels) break;
            result.add(new DepthLevel(OrderSide.BUY, e.getKey(), aggregateQty(e.getValue())));
        }
        count = 0;
        for (Map.Entry<BigDecimal, PriceLevel> e : asks.entrySet()) {
            if (count++ >= levels) break;
            result.add(new DepthLevel(OrderSide.SELL, e.getKey(), aggregateQty(e.getValue())));
        }
        return result;
    }

    private BigDecimal aggregateQty(PriceLevel level) {
        return level.totalQuantity().setScale(8, RoundingMode.HALF_UP);
    }

    public record DepthLevel(OrderSide side, BigDecimal price, BigDecimal quantity) {
    }
}
