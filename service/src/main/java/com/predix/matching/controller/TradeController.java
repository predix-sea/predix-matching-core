package com.predix.matching.controller;

import com.predix.matching.controller.dto.ApiResponse;
import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.controller.dto.TradeResponse;
import com.predix.matching.service.TradeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Tag(name = "Trades", description = "Trade history")
public class TradeController {

    private final TradeQueryService tradeQueryService;

    @GetMapping
    @Operation(summary = "List trades")
    public ApiResponse<PageResponse<TradeResponse>> listTrades(
            @RequestParam(required = false) String marketId,
            @RequestParam(required = false) String outcomeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tradeQueryService.listTrades(marketId, outcomeId, page, size));
    }
}
