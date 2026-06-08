package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderBookEntity;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderBookStatus;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.repository.OrderBookRepository;
import com.predix.matching.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderBookReconciliationServiceTest {

    @Mock
    private OrderBookRepository orderBookRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingCoreClient matchingCoreClient;

    @Mock
    private OrderBookWarmupService orderBookWarmupService;

    @InjectMocks
    private OrderBookReconciliationService reconciliationService;

    private PredixProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PredixProperties();
        properties.getReconciliation().setDepthLevels(5);
        reconciliationService = new OrderBookReconciliationService(
                orderBookRepository,
                orderRepository,
                matchingCoreClient,
                orderBookWarmupService,
                properties
        );
    }

    @Test
    void reconcileBook_noDrift_skipsWarmup() {
        OrderEntity buy = openLimitOrder(OrderSide.BUY, "0.50", "10");
        when(orderRepository.findOpenOrders("m1", "yes")).thenReturn(List.of(buy));
        when(matchingCoreClient.getDepth("m1", "yes", 5)).thenReturn(List.of(
                depthLevel(OrderSide.BUY, "0.50", "10")
        ));

        assertThat(reconciliationService.reconcileBook("m1", "yes")).isFalse();
        verify(orderBookWarmupService, never()).warmupBookFromDb(anyString(), anyString());
    }

    @Test
    void reconcileBook_drift_triggersWarmup() {
        OrderEntity buy = openLimitOrder(OrderSide.BUY, "0.50", "10");
        when(orderRepository.findOpenOrders("m1", "yes")).thenReturn(List.of(buy));
        when(matchingCoreClient.getDepth("m1", "yes", 5)).thenReturn(List.of(
                depthLevel(OrderSide.BUY, "0.49", "10")
        ));

        assertThat(reconciliationService.reconcileBook("m1", "yes")).isTrue();
        verify(orderBookWarmupService).warmupBookFromDb("m1", "yes");
    }

    @Test
    void reconcileAllActiveBooks_countsDriftingBooks() {
        OrderBookEntity active = OrderBookEntity.builder()
                .marketId("m1")
                .outcomeId("yes")
                .status(OrderBookStatus.ACTIVE)
                .build();
        OrderBookEntity closed = OrderBookEntity.builder()
                .marketId("m2")
                .outcomeId("no")
                .status(OrderBookStatus.CLOSED)
                .build();
        when(orderBookRepository.findAll()).thenReturn(List.of(active, closed));

        OrderEntity buy = openLimitOrder(OrderSide.BUY, "0.50", "10");
        when(orderRepository.findOpenOrders("m1", "yes")).thenReturn(List.of(buy));
        when(matchingCoreClient.getDepth("m1", "yes", 5)).thenReturn(List.of(
                depthLevel(OrderSide.BUY, "0.49", "10")
        ));

        assertThat(reconciliationService.reconcileAllActiveBooks()).isEqualTo(1);
        verify(orderBookWarmupService).warmupBookFromDb("m1", "yes");
        verify(orderBookWarmupService, never()).warmupBookFromDb("m2", "no");
    }

    @Test
    void computeDepthFromDb_aggregatesSamePriceLevel() {
        OrderEntity first = openLimitOrder(OrderSide.BUY, "0.50", "4");
        OrderEntity second = openLimitOrder(OrderSide.BUY, "0.50", "6");
        when(orderRepository.findOpenOrders("m1", "yes")).thenReturn(List.of(first, second));

        List<CoreMatchResult.CoreDepthLevel> depth =
                reconciliationService.computeDepthFromDb("m1", "yes", 5);

        assertThat(depth).hasSize(1);
        assertThat(depth.getFirst().getSide()).isEqualTo(OrderSide.BUY);
        assertThat(depth.getFirst().getPrice()).isEqualByComparingTo("0.50");
        assertThat(depth.getFirst().getQuantity()).isEqualByComparingTo("10");
    }

    private static OrderEntity openLimitOrder(OrderSide side, String price, String quantity) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .marketId("m1")
                .outcomeId("yes")
                .userId("user-1")
                .side(side)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(quantity))
                .remainingQuantity(new BigDecimal(quantity))
                .status(OrderStatus.NEW)
                .build();
    }

    private static CoreMatchResult.CoreDepthLevel depthLevel(OrderSide side, String price, String quantity) {
        return CoreMatchResult.CoreDepthLevel.builder()
                .side(side)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(quantity))
                .build();
    }
}
