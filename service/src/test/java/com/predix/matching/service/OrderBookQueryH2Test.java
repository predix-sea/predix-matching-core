package com.predix.matching.service;

import com.predix.matching.controller.dto.DepthLevelResponse;
import com.predix.matching.controller.dto.OrderBookResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.support.H2IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookQueryH2Test extends H2IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderBookService orderBookService;

    @Autowired
    private TradeQueryService tradeQueryService;

    @Test
    void orderBookAndTradesQuery() {
        marketSchemaClient.setMarketStatus("mkt-query", com.predix.matching.domain.enums.MarketStatus.OPEN);

        PlaceOrderRequest sell = new PlaceOrderRequest();
        sell.setMarketId("mkt-query");
        sell.setOutcomeId("yes");
        sell.setUserId("seller-q");
        sell.setSide(OrderSide.SELL);
        sell.setOrderType(OrderType.LIMIT);
        sell.setPrice(new BigDecimal("0.62"));
        sell.setQuantity(new BigDecimal("5"));
        sell.setClientOrderId("q-sell");
        orderService.placeOrder(sell);

        OrderBookResponse book = orderBookService.getOrderBook("mkt-query", "yes");
        assertThat(book.getMarketId()).isEqualTo("mkt-query");

        List<DepthLevelResponse> depth = orderBookService.getDepth("mkt-query", "yes", 5);
        assertThat(depth).isNotEmpty();

        assertThat(tradeQueryService.listTrades("mkt-query", "yes", 0, 10).getContent()).isEmpty();
    }
}
