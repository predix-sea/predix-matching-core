package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
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
class PendingCancelRecoveryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingCoreClient matchingCoreClient;

    @Mock
    private OrderMatchPersistenceService orderMatchPersistenceService;

    private PendingCancelRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        PredixProperties properties = new PredixProperties();
        properties.getPendingCancel().setBatchSize(10);
        recoveryService = new PendingCancelRecoveryService(
                orderRepository, matchingCoreClient, orderMatchPersistenceService, properties);
    }

    @Test
    void recoverPendingCancels_finalizesStuckOrders() {
        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .marketId("m1")
                .outcomeId("yes")
                .userId("user-1")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("0.50"))
                .quantity(new BigDecimal("10"))
                .remainingQuantity(new BigDecimal("10"))
                .status(OrderStatus.PENDING_CANCEL)
                .build();

        when(orderRepository.findByStatusOrderByUpdatedAtAsc(eq(OrderStatus.PENDING_CANCEL), any(Pageable.class)))
                .thenReturn(List.of(order));
        when(matchingCoreClient.cancelOrder(order)).thenReturn(true);

        assertThat(recoveryService.recoverPendingCancels()).isEqualTo(1);
        verify(orderMatchPersistenceService).finalizeCancel(order.getId());
    }
}
