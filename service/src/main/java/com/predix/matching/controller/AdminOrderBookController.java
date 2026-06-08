package com.predix.matching.controller;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.controller.dto.ApiResponse;
import com.predix.matching.service.OrderBookWarmupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/orderbooks")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "predix.admin.enabled", havingValue = "true")
public class AdminOrderBookController {

    private final OrderBookWarmupService orderBookWarmupService;
    private final MatchingCoreClient matchingCoreClient;

    @PostMapping("/reload")
    public ApiResponse<OrderBookWarmupService.WarmupSummary> reloadAll() {
        return ApiResponse.success(orderBookWarmupService.warmupAllOpenBooks());
    }

    @PostMapping("/{marketId}/{outcomeId}/reload")
    public ApiResponse<Map<String, Object>> reloadBook(
            @PathVariable String marketId,
            @PathVariable String outcomeId) {
        matchingCoreClient.resetBook(marketId, outcomeId);
        int loaded = orderBookWarmupService.warmupBookFromDb(marketId, outcomeId);
        return ApiResponse.success(Map.of(
                "marketId", marketId,
                "outcomeId", outcomeId,
                "loadedOrders", loaded
        ));
    }
}
