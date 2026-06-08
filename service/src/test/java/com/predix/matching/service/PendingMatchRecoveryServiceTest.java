package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.idempotency.IdempotencyService;
import com.predix.matching.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingMatchRecoveryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingCoreClient matchingCoreClient;

    @Mock
    private OrderMatchPersistenceService orderMatchPersistenceService;

    @Mock
    private IdempotencyService idempotencyService;

    private PendingMatchRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        PredixProperties properties = new PredixProperties();
        properties.getPendingMatch().setBatchSize(10);
        recoveryService = new PendingMatchRecoveryService(
                orderRepository,
                matchingCoreClient,
                orderMatchPersistenceService,
                idempotencyService,
                properties
        );
    }

    @Test
    void recoverPendingMatches_finalizesStuckOrders() {
        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .userId("user-1")
                .clientOrderId("client-1")
                .marketId("m1")
                .outcomeId("yes")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("0.50"))
                .quantity(new BigDecimal("10"))
                .remainingQuantity(new BigDecimal("10"))
                .status(OrderStatus.PENDING_MATCH)
                .build();

        CoreMatchResult matchResult = CoreMatchResult.builder()
                .orderId(order.getId())
                .remainingQuantity(new BigDecimal("10"))
                .fills(List.of())
                .build();

        when(orderRepository.findByStatusOrderByUpdatedAtAsc(eq(OrderStatus.PENDING_MATCH), any(Pageable.class)))
                .thenReturn(List.of(order));
        when(idempotencyService.buildOrderKey("user-1", "client-1")).thenReturn("key-1");
        when(matchingCoreClient.submitOrder(order)).thenReturn(matchResult);

        assertThat(recoveryService.recoverPendingMatches()).isEqualTo(1);
        verify(orderMatchPersistenceService).finalizeMatch(order.getId(), matchResult, "key-1");
    }
}
