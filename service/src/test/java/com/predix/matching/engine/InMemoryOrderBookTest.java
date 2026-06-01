package com.predix.matching.engine;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.BookOrder;
import com.predix.matching.engine.model.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderBookTest {

    private InMemoryOrderBook book;

    @BeforeEach
    void setUp() {
        book = new InMemoryOrderBook("mkt-1", "yes");
    }

    @Test
    void limitBuyMatchesLowestAskFirst_pricePriority() {
        book.addToBook(limitOrder(OrderSide.SELL, "0.60", "10", 1));
        book.addToBook(limitOrder(OrderSide.SELL, "0.55", "10", 2));
        book.addToBook(limitOrder(OrderSide.SELL, "0.50", "10", 3));

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "0.60", "15", 4));

        assertThat(result.getFills()).hasSize(2);
        assertThat(result.getFills().get(0).getPrice()).isEqualByComparingTo("0.50");
        assertThat(result.getFills().get(1).getPrice()).isEqualByComparingTo("0.55");
    }

    @Test
    void samePriceFifo_timePriority() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        book.addToBook(bookOrder(first, OrderSide.SELL, "0.50", "5", 1));
        book.addToBook(bookOrder(second, OrderSide.SELL, "0.50", "5", 2));

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "0.50", "5", 3));

        assertThat(result.getFills()).hasSize(1);
        assertThat(result.getFills().get(0).getMakerOrderId()).isEqualTo(first);
    }

    @Test
    void marketOrderWithNoLiquidity_rejected() {
        MatchResult result = book.match(limitOrder(OrderSide.BUY, null, "5", 1).toBuilder()
                .orderType(OrderType.MARKET)
                .price(null)
                .build());

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getFills()).isEmpty();
    }

    @Test
    void marketOrderPartialFill_whenLiquidityInsufficient() {
        book.addToBook(limitOrder(OrderSide.SELL, "0.50", "3", 1));
        BookOrder marketBuy = limitOrder(OrderSide.BUY, null, "10", 2).toBuilder()
                .orderType(OrderType.MARKET)
                .price(null)
                .build();

        MatchResult result = book.match(marketBuy);

        assertThat(result.getFills()).hasSize(1);
        assertThat(result.getFills().get(0).getQuantity()).isEqualByComparingTo("3");
        assertThat(result.getIncomingOrder().getRemainingQuantity()).isEqualByComparingTo("7");
        assertThat(result.isFullyFilled()).isFalse();
        assertThat(result.isRejected()).isFalse();
    }

    @Test
    void restingLimitAddedWhenNoMatch() {
        MatchResult result = book.match(limitOrder(OrderSide.BUY, "0.40", "10", 1));

        assertThat(result.getFills()).isEmpty();
        assertThat(result.getIncomingOrder().getRemainingQuantity()).isEqualByComparingTo("10");
        assertThat(book.getDepth(5)).isNotEmpty();
    }

    private static BookOrder limitOrder(OrderSide side, String price, String qty, long seq) {
        return bookOrder(UUID.randomUUID(), side, price, qty, seq);
    }

    private static BookOrder bookOrder(UUID id, OrderSide side, String price, String qty, long seq) {
        return BookOrder.builder()
                .id(id)
                .userId("user-" + seq)
                .side(side)
                .orderType(OrderType.LIMIT)
                .price(price != null ? new BigDecimal(price) : null)
                .remainingQuantity(new BigDecimal(qty))
                .createdAt(Instant.now().plusSeconds(seq))
                .sequence(seq)
                .build();
    }
}
