package com.predix.matching.service;

import com.predix.matching.client.impl.MockMarketSchemaClient;
import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.repository.OrderRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class OrderServiceIdempotencyTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private MockMarketSchemaClient marketSchemaClient;

    @Test
    void duplicateClientOrderId_returnsSameOrder() {
        marketSchemaClient.setMarketStatus("mkt-idem", com.predix.matching.domain.enums.MarketStatus.OPEN);

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setMarketId("mkt-idem");
        request.setOutcomeId("yes");
        request.setUserId("user-1");
        request.setSide(OrderSide.BUY);
        request.setOrderType(OrderType.LIMIT);
        request.setPrice(new BigDecimal("0.50"));
        request.setQuantity(new BigDecimal("10"));
        request.setClientOrderId("client-idem-001");

        OrderResponse first = orderService.placeOrder(request);
        OrderResponse second = orderService.placeOrder(request);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(orderRepository.findAll().stream()
                .filter(o -> "client-idem-001".equals(o.getClientOrderId())).count()).isEqualTo(1);
    }
}
