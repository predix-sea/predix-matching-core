package com.predix.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.idempotency.IdempotencyService;
import com.predix.matching.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final OrderValidationService validationService;
    private final MarketLifecycleService marketLifecycleService;
    private final OrderMatchPersistenceService orderMatchPersistenceService;
    private final IdempotencyService idempotencyService;
    private final DtoMapper dtoMapper;
    private final ObjectMapper objectMapper;

    public OrderResponse placeOrder(PlaceOrderRequest request) {
        validationService.validate(request);
        marketLifecycleService.validateForPlaceOrder(request.getMarketId(), request.getOutcomeId());

        String idempotencyKey = idempotencyService.buildOrderKey(request.getUserId(), request.getClientOrderId());

        Optional<OrderEntity> existing = orderRepository.findByUserIdAndClientOrderId(
                request.getUserId(), request.getClientOrderId());
        if (existing.isPresent()) {
            Optional<String> cachedForExisting = idempotencyService.getCachedResponse(idempotencyKey);
            if (cachedForExisting.isPresent()) {
                try {
                    return objectMapper.readValue(cachedForExisting.get(), OrderResponse.class);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached order response", e);
                }
            }
            OrderEntity order = existing.get();
            if (needsMatching(order)) {
                return matchAndFinalize(order, idempotencyKey);
            }
            return dtoMapper.toOrderResponse(order);
        }

        Optional<String> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), OrderResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached order response", e);
            }
        }

        OrderEntity order = orderMatchPersistenceService.persistNewOrder(request);
        return matchAndFinalize(order, idempotencyKey);
    }

    public OrderResponse cancelOrder(UUID orderId) {
        OrderEntity order = orderMatchPersistenceService.lockOrderForCancel(orderId);
        marketLifecycleService.validateForCancel(order.getMarketId());
        return cancelAndFinalize(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));
        return dtoMapper.toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(String marketId, String userId, OrderStatus status, int page, int size) {
        Page<OrderEntity> result = orderRepository.findByFilters(marketId, userId, status, PageRequest.of(page, size));
        return dtoMapper.toPage(result.map(dtoMapper::toOrderResponse));
    }

    private OrderResponse matchAndFinalize(OrderEntity order, String idempotencyKey) {
        CoreMatchResult matchResult;
        try {
            matchResult = matchingCoreClient.submitOrder(order);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.MATCHING_CORE_UNCERTAIN) {
                log.warn("Uncertain gRPC submit for orderId={}; marking PENDING_MATCH", order.getId(), e);
                orderMatchPersistenceService.markPendingMatch(order.getId());
            }
            throw e;
        }

        if (matchResult.isRejected()) {
            orderMatchPersistenceService.finalizeRejected(order.getId(), matchResult, idempotencyKey);
            throw new BusinessException(ErrorCode.ORDER_INSUFFICIENT_LIQUIDITY, matchResult.getRejectReason());
        }

        try {
            return orderMatchPersistenceService.finalizeMatch(order.getId(), matchResult, idempotencyKey);
        } catch (RuntimeException e) {
            log.error("finalizeMatch failed after gRPC submit for orderId={}", order.getId(), e);
            orderMatchPersistenceService.markPendingMatch(order.getId());
            throw e;
        }
    }

    private OrderResponse cancelAndFinalize(OrderEntity order) {
        try {
            matchingCoreClient.cancelOrder(order);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.MATCHING_CORE_UNCERTAIN) {
                log.warn("Uncertain gRPC cancel for orderId={}; marking PENDING_CANCEL", order.getId(), e);
                orderMatchPersistenceService.markPendingCancel(order.getId());
            }
            throw e;
        }

        try {
            return orderMatchPersistenceService.finalizeCancel(order.getId());
        } catch (RuntimeException e) {
            log.error("finalizeCancel failed after gRPC cancel for orderId={}", order.getId(), e);
            orderMatchPersistenceService.markPendingCancel(order.getId());
            throw e;
        }
    }

    private static boolean needsMatching(OrderEntity order) {
        return order.getStatus() == OrderStatus.NEW
                || order.getStatus() == OrderStatus.PARTIAL
                || order.getStatus() == OrderStatus.PENDING_MATCH;
    }
}
