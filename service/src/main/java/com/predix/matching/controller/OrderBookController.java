package com.predix.matching.controller;

import com.predix.matching.controller.dto.ApiResponse;
import com.predix.matching.controller.dto.DepthLevelResponse;
import com.predix.matching.controller.dto.OrderBookResponse;
import com.predix.matching.service.OrderBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orderbooks")
@RequiredArgsConstructor
@Tag(name = "Order Books", description = "Order book queries")
public class OrderBookController {

    private final OrderBookService orderBookService;

    @GetMapping("/{marketId}/{outcomeId}")
    @Operation(summary = "Get order book metadata")
    public ApiResponse<OrderBookResponse> getOrderBook(
            @PathVariable String marketId,
            @PathVariable String outcomeId) {
        return ApiResponse.success(orderBookService.getOrderBook(marketId, outcomeId));
    }

    @GetMapping("/{marketId}/{outcomeId}/depth")
    @Operation(summary = "Get order book depth")
    public ApiResponse<List<DepthLevelResponse>> getDepth(
            @PathVariable String marketId,
            @PathVariable String outcomeId,
            @RequestParam(defaultValue = "10") int levels) {
        return ApiResponse.success(orderBookService.getDepth(marketId, outcomeId, levels));
    }
}
