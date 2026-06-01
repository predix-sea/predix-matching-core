package com.predix.matching.engine;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.model.BookOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderBookCancelTest {

    @Test
    void removeFromBook_removesRestingOrder() {
        InMemoryOrderBook book = new InMemoryOrderBook("m", "yes");
        UUID id = UUID.randomUUID();
        book.addToBook(BookOrder.builder()
                .id(id)
                .userId("u")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("0.40"))
                .remainingQuantity(new BigDecimal("10"))
                .createdAt(Instant.now())
                .sequence(1)
                .build());

        assertThat(book.removeFromBook(id)).isTrue();
        assertThat(book.getDepth(5).stream()
                .filter(d -> d.side() == OrderSide.BUY).count()).isZero();
    }
}
