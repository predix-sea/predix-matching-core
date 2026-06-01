package com.predix.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.controller.dto.TradeResponse;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.entity.TradeEntity;
import com.predix.matching.domain.enums.EngineEventType;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.engine.MatchingEngine;
import com.predix.matching.engine.OrderStatusTransition;
import com.predix.matching.engine.model.TradeFill;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import com.predix.matching.idempotency.IdempotencyService;
import com.predix.matching.repository.OrderRepository;
import com.predix.matching.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final OrderValidationService validationService;
    private final MarketLifecycleService marketLifecycleService;
    private final OrderBookService orderBookService;
    private final CodeGenerator codeGenerator;
    private final EngineEventService engineEventService;
    private final ExecutionTaskService executionTaskService;
    private final IdempotencyService idempotencyService;
    private final DtoMapper dtoMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        validationService.validate(request);
        marketLifecycleService.validateForPlaceOrder(request.getMarketId(), request.getOutcomeId());

        String idempotencyKey = idempotencyService.buildOrderKey(request.getUserId(), request.getClientOrderId());

        Optional<OrderEntity> existing = orderRepository.findByUserIdAndClientOrderId(
                request.getUserId(), request.getClientOrderId());
        if (existing.isPresent()) {
            return dtoMapper.toOrderResponse(existing.get());
        }

        Optional<String> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), OrderResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached order response", e);
            }
        }

        orderBookService.ensureOrderBook(request.getMarketId(), request.getOutcomeId());

        OrderEntity order = OrderEntity.builder()
                .orderCode(codeGenerator.orderCode())
                .marketId(request.getMarketId())
                .outcomeId(request.getOutcomeId())
                .userId(request.getUserId())
                .side(request.getSide())
                .orderType(request.getOrderType())
                .price(request.getOrderType() == OrderType.LIMIT ? request.getPrice() : null)
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .status(OrderStatus.NEW)
                .clientOrderId(request.getClientOrderId())
                .build();
        order = orderRepository.save(order);

        Map<String, Object> createdPayload = Map.of(
                "orderId", order.getId().toString(),
                "marketId", order.getMarketId(),
                "side", order.getSide().name()
        );
        engineEventService.recordAndPublish(EngineEventType.ORDER_CREATED, order.getId().toString(), createdPayload);

        CoreMatchResult matchResult = matchingCoreClient.submitOrder(order);
        List<TradeResponse> trades = processFills(order, matchResult);

        order.setRemainingQuantity(matchResult.getRemainingQuantity());
        order.setStatus(MatchingEngine.resolveStatusAfterMatch(order.getQuantity(), order.getRemainingQuantity()));
        order = orderRepository.save(order);

        if (!trades.isEmpty()) {
            engineEventService.recordAndPublish(EngineEventType.ORDER_MATCHED, order.getId().toString(),
                    Map.of("orderId", order.getId().toString(), "fillCount", trades.size()));
        }

        OrderResponse response = dtoMapper.toOrderResponse(order, trades);
        idempotencyService.saveResponse(idempotencyKey, "ORDER", order.getId().toString(), response);
        return response;
    }

    private List<TradeResponse> processFills(OrderEntity takerOrder, CoreMatchResult matchResult) {
        List<TradeResponse> tradeResponses = new ArrayList<>();
        for (CoreMatchResult.CoreTradeFill fill : matchResult.getFills()) {
            OrderEntity makerOrder = orderRepository.findByIdForUpdate(fill.getMakerOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Maker order not found"));

            TradeFill legacyFill = TradeFill.builder()
                    .makerOrderId(fill.getMakerOrderId())
                    .makerUserId(fill.getMakerUserId())
                    .takerOrderId(fill.getTakerOrderId())
                    .takerUserId(fill.getTakerUserId())
                    .price(fill.getPrice())
                    .quantity(fill.getQuantity())
                    .buyerIsTaker(fill.isBuyerIsTaker())
                    .build();
            MatchingEngine.applyFillToMaker(makerOrder, legacyFill);
            orderRepository.save(makerOrder);

            UUID buyOrderId = fill.isBuyerIsTaker() ? fill.getTakerOrderId() : fill.getMakerOrderId();
            UUID sellOrderId = fill.isBuyerIsTaker() ? fill.getMakerOrderId() : fill.getTakerOrderId();

            BigDecimal notional = fill.getPrice().multiply(fill.getQuantity()).setScale(8, RoundingMode.HALF_UP);
            TradeEntity trade = TradeEntity.builder()
                    .tradeCode(codeGenerator.tradeCode())
                    .marketId(takerOrder.getMarketId())
                    .outcomeId(takerOrder.getOutcomeId())
                    .buyOrderId(buyOrderId)
                    .sellOrderId(sellOrderId)
                    .price(fill.getPrice())
                    .quantity(fill.getQuantity())
                    .notional(notional)
                    .makerUserId(fill.getMakerUserId())
                    .takerUserId(fill.getTakerUserId())
                    .build();
            trade = tradeRepository.save(trade);

            Map<String, Object> tradePayload = new HashMap<>();
            tradePayload.put("tradeId", trade.getId().toString());
            tradePayload.put("tradeCode", trade.getTradeCode());
            tradePayload.put("price", fill.getPrice().toPlainString());
            tradePayload.put("quantity", fill.getQuantity().toPlainString());
            tradePayload.put("marketId", trade.getMarketId());
            tradePayload.put("outcomeId", trade.getOutcomeId());

            engineEventService.recordAndPublish(EngineEventType.TRADE_EXECUTED, trade.getId().toString(), tradePayload);
            executionTaskService.createTradeSubmitTask(trade.getMarketId(), trade.getId(), trade.getTradeCode(), tradePayload);

            tradeResponses.add(dtoMapper.toTradeResponse(trade));
        }
        return tradeResponses;
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));

        if (order.getStatus().isFinal()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_FINALIZED);
        }
        if (!order.getStatus().canCancel()) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_TRANSITION);
        }

        marketLifecycleService.validateForCancel(order.getMarketId());
        OrderStatusTransition.validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        matchingCoreClient.cancelOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setRemainingQuantity(BigDecimal.ZERO);
        order = orderRepository.save(order);

        Map<String, Object> payload = Map.of("orderId", order.getId().toString());
        engineEventService.recordAndPublish(EngineEventType.ORDER_CANCELLED, order.getId().toString(), payload);
        executionTaskService.createCancelTask(order.getMarketId(), order.getId(), payload);

        return dtoMapper.toOrderResponse(order);
    }

    public OrderResponse getOrder(UUID id) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));
        return dtoMapper.toOrderResponse(order);
    }

    public PageResponse<OrderResponse> listOrders(String marketId, String userId, OrderStatus status, int page, int size) {
        Page<OrderEntity> result = orderRepository.findByFilters(marketId, userId, status, PageRequest.of(page, size));
        return dtoMapper.toPage(result.map(dtoMapper::toOrderResponse));
    }
}
