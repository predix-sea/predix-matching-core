package com.predix.matching.controller;

import com.predix.matching.controller.dto.*;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order")
    public ApiResponse<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.success(orderService.placeOrder(request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.cancelOrder(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by id")
    public ApiResponse<OrderResponse> getOrder(@PathVariable UUID id) {
        return ApiResponse.success(orderService.getOrder(id));
    }

    @GetMapping
    @Operation(summary = "List orders with filters")
    public ApiResponse<PageResponse<OrderResponse>> listOrders(
            @RequestParam(required = false) String marketId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(orderService.listOrders(marketId, userId, status, page, size));
    }
}
