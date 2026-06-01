package com.predix.matching.engine;

import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.BookOrder;
import com.predix.matching.engine.model.MatchResult;
import com.predix.matching.engine.model.TradeFill;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MatchingEngine {

    private final OrderBookRegistry registry;

    public MatchResult processOrder(OrderEntity order) {
        InMemoryOrderBook book = registry.getOrCreate(order.getMarketId(), order.getOutcomeId());
        BookOrder incoming = toBookOrder(order);

        MatchResult result = book.match(incoming);

        if (result.isRejected()) {
            throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_LIQUIDITY, result.getRejectReason());
        }

        return result;
    }

    public boolean cancelFromBook(OrderEntity order) {
        InMemoryOrderBook book = registry.get(order.getMarketId(), order.getOutcomeId());
        if (book == null) {
            return false;
        }
        return book.removeFromBook(order.getId());
    }

    public static OrderStatus resolveStatusAfterMatch(BigDecimal originalQty, BigDecimal remaining) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return OrderStatus.FILLED;
        }
        if (remaining.compareTo(originalQty) < 0) {
            return OrderStatus.PARTIAL;
        }
        return OrderStatus.NEW;
    }

    public static BookOrder toBookOrder(OrderEntity order) {
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

    public static void applyFillToMaker(OrderEntity maker, TradeFill fill) {
        BigDecimal newRemaining = maker.getRemainingQuantity().subtract(fill.getQuantity());
        maker.setRemainingQuantity(newRemaining.max(BigDecimal.ZERO));
        maker.setStatus(OrderStatusTransition.afterFill(maker.getRemainingQuantity()));
    }

    public static boolean isBuyTaker(OrderSide incomingSide) {
        return incomingSide == OrderSide.BUY;
    }
}
