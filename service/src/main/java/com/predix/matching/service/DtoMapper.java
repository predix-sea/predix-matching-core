package com.predix.matching.service;

import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.controller.dto.*;
import com.predix.matching.domain.entity.ExecutionTaskEntity;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.entity.TradeEntity;
import com.predix.matching.engine.InMemoryOrderBook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DtoMapper {

    public OrderResponse toOrderResponse(OrderEntity order, List<TradeResponse> trades) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .marketId(order.getMarketId())
                .outcomeId(order.getOutcomeId())
                .userId(order.getUserId())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .status(order.getStatus())
                .clientOrderId(order.getClientOrderId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .trades(trades != null ? trades : Collections.emptyList())
                .build();
    }

    public OrderResponse toOrderResponse(OrderEntity order) {
        return toOrderResponse(order, Collections.emptyList());
    }

    public TradeResponse toTradeResponse(TradeEntity trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .tradeCode(trade.getTradeCode())
                .marketId(trade.getMarketId())
                .outcomeId(trade.getOutcomeId())
                .buyOrderId(trade.getBuyOrderId())
                .sellOrderId(trade.getSellOrderId())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .notional(trade.getNotional())
                .makerUserId(trade.getMakerUserId())
                .takerUserId(trade.getTakerUserId())
                .createdAt(trade.getCreatedAt())
                .build();
    }

    public ExecutionTaskResponse toTaskResponse(ExecutionTaskEntity task) {
        return ExecutionTaskResponse.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .marketId(task.getMarketId())
                .taskType(task.getTaskType())
                .payload(task.getPayload())
                .status(task.getStatus())
                .retryCount(task.getRetryCount())
                .nextRetryAt(task.getNextRetryAt())
                .idempotencyKey(task.getIdempotencyKey())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    public DepthLevelResponse toDepth(CoreMatchResult.CoreDepthLevel level) {
        return DepthLevelResponse.builder()
                .side(level.getSide())
                .price(level.getPrice())
                .quantity(level.getQuantity())
                .build();
    }

    public DepthLevelResponse toDepth(InMemoryOrderBook.DepthLevel level) {
        return DepthLevelResponse.builder()
                .side(level.side())
                .price(level.price())
                .quantity(level.quantity())
                .build();
    }

    public <T> PageResponse<T> toPage(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
