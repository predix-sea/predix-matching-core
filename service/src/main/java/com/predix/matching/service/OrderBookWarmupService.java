package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookWarmupService {

    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;

    public WarmupSummary warmupAllOpenBooks() {
        List<OrderEntity> openOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.PARTIAL)
                .toList();

        Map<String, List<CoreMatchResult.CoreBookOrder>> grouped = new HashMap<>();
        for (OrderEntity order : openOrders) {
            String key = order.getMarketId() + ":" + order.getOutcomeId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(toCoreBookOrder(order));
        }

        int total = 0;
        for (var entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            total += warmupBook(parts[0], parts[1], entry.getValue());
        }
        log.info("Order book warmup loaded {} open orders across {} books", total, grouped.size());
        return new WarmupSummary(total, grouped.size());
    }

    public int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders) {
        return matchingCoreClient.warmupBook(marketId, outcomeId, orders, true);
    }

    public int warmupBookFromDb(String marketId, String outcomeId) {
        List<OrderEntity> openOrders = orderRepository.findOpenOrders(marketId, outcomeId);
        List<CoreMatchResult.CoreBookOrder> bookOrders = openOrders.stream().map(this::toCoreBookOrder).toList();
        return warmupBook(marketId, outcomeId, bookOrders);
    }

    private CoreMatchResult.CoreBookOrder toCoreBookOrder(OrderEntity order) {
        return CoreMatchResult.CoreBookOrder.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .price(order.getPrice())
                .remainingQuantity(order.getRemainingQuantity())
                .createdAtEpochMs(order.getCreatedAt() != null
                        ? order.getCreatedAt().toEpochMilli() : System.currentTimeMillis())
                .sequence(0)
                .build();
    }

    public record WarmupSummary(int loadedOrders, int bookCount) {
    }
}
