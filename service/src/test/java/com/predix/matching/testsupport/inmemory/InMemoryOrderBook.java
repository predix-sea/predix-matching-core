package com.predix.matching.testsupport.inmemory;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.TradeFill;
import com.predix.matching.testsupport.inmemory.model.BookOrder;
import com.predix.matching.testsupport.inmemory.model.MatchResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** H2 test-only in-memory book; production matching runs in the C++ core. */
public class InMemoryOrderBook {

    private final TreeMap<BigDecimal, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, PriceLevel> asks = new TreeMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final Map<UUID, BookOrder> orderIndex = new ConcurrentHashMap<>();
    private final Map<UUID, MatchResult> submissionCache = new ConcurrentHashMap<>();

    public void clear() {
        bids.clear();
        asks.clear();
        orderIndex.clear();
        submissionCache.clear();
        sequence.set(0);
    }

    public MatchResult match(BookOrder incoming) {
        MatchResult cached = submissionCache.get(incoming.getId());
        if (cached != null) {
            return cached;
        }
        if (orderIndex.containsKey(incoming.getId())) {
            return MatchResult.builder()
                    .incomingOrder(orderIndex.get(incoming.getId()))
                    .fills(List.of())
                    .fullyFilled(false)
                    .rejected(false)
                    .build();
        }

        List<TradeFill> fills = new ArrayList<>();
        BigDecimal remaining = incoming.getRemainingQuantity();
        TreeMap<BigDecimal, PriceLevel> opposite = incoming.getSide() == OrderSide.BUY ? asks : bids;

        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, PriceLevel> best = opposite.firstEntry();
            if (!canMatchPrice(incoming, best.getKey())) {
                break;
            }
            PriceLevel level = best.getValue();
            while (remaining.compareTo(BigDecimal.ZERO) > 0 && !level.isEmpty()) {
                BookOrder maker = level.peek();
                if (maker == null) {
                    break;
                }
                BigDecimal fillQty = remaining.min(maker.getRemainingQuantity());
                fills.add(TradeFill.builder()
                        .makerOrderId(maker.getId())
                        .makerUserId(maker.getUserId())
                        .takerOrderId(incoming.getId())
                        .takerUserId(incoming.getUserId())
                        .price(maker.getPrice())
                        .quantity(fillQty)
                        .buyerIsTaker(incoming.getSide() == OrderSide.BUY)
                        .build());
                remaining = remaining.subtract(fillQty);
                BigDecimal makerRemaining = maker.getRemainingQuantity().subtract(fillQty);
                if (makerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    level.poll();
                    orderIndex.remove(maker.getId());
                } else {
                    BookOrder updatedMaker = maker.toBuilder().remainingQuantity(makerRemaining).build();
                    level.poll();
                    level.requeueFront(updatedMaker);
                    orderIndex.put(updatedMaker.getId(), updatedMaker);
                }
            }
            if (level.isEmpty()) {
                opposite.remove(best.getKey());
            }
        }

        BookOrder updatedIncoming = incoming.toBuilder().remainingQuantity(remaining).build();
        boolean fullyFilled = remaining.compareTo(BigDecimal.ZERO) <= 0;
        boolean rejected = false;
        String rejectReason = null;
        if (!fullyFilled) {
            if (incoming.getOrderType() == OrderType.MARKET && fills.isEmpty()) {
                rejected = true;
                rejectReason = "No liquidity for market order";
            } else if (incoming.getOrderType() == OrderType.LIMIT) {
                addToBook(updatedIncoming);
            }
        }

        MatchResult result = MatchResult.builder()
                .incomingOrder(updatedIncoming)
                .fills(fills)
                .fullyFilled(fullyFilled)
                .rejected(rejected)
                .rejectReason(rejectReason)
                .build();
        submissionCache.put(result.getIncomingOrder().getId(), result);
        return result;
    }

    public void addToBook(BookOrder order) {
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0 || orderIndex.containsKey(order.getId())) {
            return;
        }
        TreeMap<BigDecimal, PriceLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
        PriceLevel level = side.computeIfAbsent(order.getPrice(), PriceLevel::new);
        BookOrder withSeq = order.toBuilder().sequence(sequence.incrementAndGet()).build();
        level.add(withSeq);
        orderIndex.put(withSeq.getId(), withSeq);
    }

    public boolean removeFromBook(UUID orderId) {
        BookOrder order = orderIndex.remove(orderId);
        submissionCache.remove(orderId);
        if (order == null) {
            return false;
        }
        TreeMap<BigDecimal, PriceLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
        PriceLevel level = side.get(order.getPrice());
        if (level == null) {
            return false;
        }
        Deque<BookOrder> temp = new ArrayDeque<>();
        boolean found = false;
        while (!level.isEmpty()) {
            BookOrder o = level.poll();
            if (o.getId().equals(orderId)) {
                found = true;
                break;
            }
            temp.addLast(o);
        }
        temp.forEach(level::add);
        if (level.isEmpty()) {
            side.remove(order.getPrice());
        }
        return found;
    }

    public List<DepthLevel> getDepth(int levels) {
        List<DepthLevel> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<BigDecimal, PriceLevel> e : bids.entrySet()) {
            if (count++ >= levels) {
                break;
            }
            result.add(new DepthLevel(OrderSide.BUY, e.getKey(), aggregateQty(e.getValue())));
        }
        count = 0;
        for (Map.Entry<BigDecimal, PriceLevel> e : asks.entrySet()) {
            if (count++ >= levels) {
                break;
            }
            result.add(new DepthLevel(OrderSide.SELL, e.getKey(), aggregateQty(e.getValue())));
        }
        return result;
    }

    private static boolean canMatchPrice(BookOrder incoming, BigDecimal oppositePrice) {
        if (incoming.getOrderType() == OrderType.MARKET) {
            return true;
        }
        if (incoming.getSide() == OrderSide.BUY) {
            return incoming.getPrice().compareTo(oppositePrice) >= 0;
        }
        return incoming.getPrice().compareTo(oppositePrice) <= 0;
    }

    private static BigDecimal aggregateQty(PriceLevel level) {
        return level.totalQuantity().setScale(8, RoundingMode.HALF_UP);
    }

    public record DepthLevel(OrderSide side, BigDecimal price, BigDecimal quantity) {
    }
}
