package com.predix.matching.service;

import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.repository.ExecutionTaskRepository;
import com.predix.matching.repository.OrderRepository;
import com.predix.matching.repository.TradeRepository;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.support.H2IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceH2Test extends H2IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ExecutionTaskRepository executionTaskRepository;

    @Test
    void matchingFlow_persistsTradeAndTask() {
        PlaceOrderRequest sell = request("seller", OrderSide.SELL, "0.60", "20", "h2-sell-1");
        orderService.placeOrder(sell);

        OrderResponse buy = orderService.placeOrder(
                request("buyer", OrderSide.BUY, "0.60", "8", "h2-buy-1"));

        assertThat(buy.getTrades()).hasSize(1);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(tradeRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(executionTaskRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void idempotentClientOrderId_returnsSame() {
        PlaceOrderRequest req = request("u1", OrderSide.BUY, "0.45", "5", "h2-idem");
        OrderResponse a = orderService.placeOrder(req);
        OrderResponse b = orderService.placeOrder(req);
        assertThat(b.getId()).isEqualTo(a.getId());
        assertThat(orderRepository.findAll().stream()
                .filter(o -> "h2-idem".equals(o.getClientOrderId())).count()).isEqualTo(1);
    }

    @Test
    void cancelRestingOrder() {
        OrderResponse placed = orderService.placeOrder(
                request("u2", OrderSide.BUY, "0.30", "10", "h2-cancel"));
        OrderResponse cancelled = orderService.cancelOrder(placed.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectedMarketOrder_persistsRejectedStatus() {
        PlaceOrderRequest request = request("u-market", OrderSide.BUY, "0.50", "5", "h2-mkt-reject");
        request.setOrderType(OrderType.MARKET);
        request.setPrice(null);

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_INSUFFICIENT_LIQUIDITY);

        OrderResponse retry = orderService.placeOrder(request);
        assertThat(retry.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    private PlaceOrderRequest request(String userId, OrderSide side, String price, String qty, String clientId) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setMarketId("mkt-h2");
        request.setOutcomeId("yes");
        request.setUserId(userId);
        request.setSide(side);
        request.setOrderType(OrderType.LIMIT);
        request.setPrice(new BigDecimal(price));
        request.setQuantity(new BigDecimal(qty));
        request.setClientOrderId(clientId);
        return request;
    }
}
