package com.predix.matching.service;

import com.predix.matching.client.impl.MockMarketSchemaClient;
import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.repository.ExecutionTaskRepository;
import com.predix.matching.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class MatchingFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("predix_test")
            .withUsername("predix")
            .withPassword("predix");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ExecutionTaskRepository executionTaskRepository;

    @Autowired
    private MockMarketSchemaClient marketSchemaClient;

    @BeforeEach
    void setup() {
        marketSchemaClient.setMarketStatus("mkt-flow", com.predix.matching.domain.enums.MarketStatus.OPEN);
    }

    @Test
    void placeMatchingOrders_createsTradeAndExecutionTask() {
        PlaceOrderRequest sell = buildRequest("user-seller", OrderSide.SELL, "0.55", "20", "sell-001");
        orderService.placeOrder(sell);

        PlaceOrderRequest buy = buildRequest("user-buyer", OrderSide.BUY, "0.55", "10", "buy-001");
        OrderResponse result = orderService.placeOrder(buy);

        assertThat(result.getStatus()).isIn(OrderStatus.FILLED, OrderStatus.PARTIAL);
        assertThat(result.getTrades()).hasSize(1);
        assertThat(tradeRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(executionTaskRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void cancelOpenOrder_succeeds() {
        PlaceOrderRequest buy = buildRequest("user-cancel", OrderSide.BUY, "0.40", "10", "cancel-001");
        OrderResponse placed = orderService.placeOrder(buy);

        OrderResponse cancelled = orderService.cancelOrder(placed.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private PlaceOrderRequest buildRequest(String userId, OrderSide side, String price, String qty, String clientId) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setMarketId("mkt-flow");
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
