package com.predix.matching.config;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.engine.MatchingEngine;
import com.predix.matching.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWarmup implements ApplicationRunner {

    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;

    @Override
    public void run(ApplicationArguments args) {
        List<OrderEntity> openOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.PARTIAL)
                .toList();

        Map<String, List<CoreMatchResult.CoreBookOrder>> grouped = new HashMap<>();
        for (OrderEntity order : openOrders) {
            String key = order.getMarketId() + ":" + order.getOutcomeId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(
                    CoreMatchResult.CoreBookOrder.builder()
                            .id(order.getId())
                            .userId(order.getUserId())
                            .side(order.getSide())
                            .orderType(order.getOrderType())
                            .price(order.getPrice())
                            .remainingQuantity(order.getRemainingQuantity())
                            .createdAtEpochMs(order.getCreatedAt() != null
                                    ? order.getCreatedAt().toEpochMilli() : System.currentTimeMillis())
                            .sequence(0)
                            .build());
        }

        int total = 0;
        for (var entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            int loaded = matchingCoreClient.warmupBook(parts[0], parts[1], entry.getValue());
            total += loaded;
        }
        log.info("Order book warmup loaded {} open orders across {} books", total, grouped.size());
    }
}
