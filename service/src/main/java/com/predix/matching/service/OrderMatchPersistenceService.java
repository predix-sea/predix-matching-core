package com.predix.matching.service;

import com.predix.matching.controller.dto.OrderResponse;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.controller.dto.TradeResponse;
import com.predix.matching.client.dto.CoreMatchResult;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DB transactions for the place/cancel flow. gRPC calls stay outside these methods so a
 * successful match in C++ is not rolled back when persistence fails.
 */
@Service
@RequiredArgsConstructor
public class OrderMatchPersistenceService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final OrderBookService orderBookService;
    private final CodeGenerator codeGenerator;
    private final EngineEventService engineEventService;
    private final ExecutionTaskService executionTaskService;
    private final IdempotencyService idempotencyService;
    private final DtoMapper dtoMapper;

    @Transactional
    public OrderEntity persistNewOrder(PlaceOrderRequest request) {
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
        return order;
    }

    @Transactional
    public void markPendingMatch(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.NEW || order.getStatus() == OrderStatus.PARTIAL) {
            OrderStatusTransition.validateTransition(order.getStatus(), OrderStatus.PENDING_MATCH);
            order.setStatus(OrderStatus.PENDING_MATCH);
            orderRepository.save(order);
        }
    }

    @Transactional
    public OrderResponse finalizeMatch(UUID orderId, CoreMatchResult matchResult, String idempotencyKey) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));

        List<TradeResponse> trades = applyFills(order, matchResult);

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

    @Transactional
    public OrderEntity lockOrderForCancel(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));

        if (order.getStatus().isFinal()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_FINALIZED);
        }
        if (!order.getStatus().canCancel()) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_TRANSITION);
        }
        OrderStatusTransition.validateTransition(order.getStatus(), OrderStatus.CANCELLED);
        return order;
    }

    @Transactional
    public OrderResponse finalizeCancel(OrderEntity order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setRemainingQuantity(BigDecimal.ZERO);
        order = orderRepository.save(order);

        Map<String, Object> payload = Map.of("orderId", order.getId().toString());
        engineEventService.recordAndPublish(EngineEventType.ORDER_CANCELLED, order.getId().toString(), payload);
        executionTaskService.createCancelTask(order.getMarketId(), order.getId(), payload);
        return dtoMapper.toOrderResponse(order);
    }

    private List<TradeResponse> applyFills(OrderEntity takerOrder, CoreMatchResult matchResult) {
        List<TradeResponse> tradeResponses = new ArrayList<>();
        for (CoreMatchResult.CoreTradeFill fill : matchResult.getFills()) {
            UUID buyOrderId = fill.isBuyerIsTaker() ? fill.getTakerOrderId() : fill.getMakerOrderId();
            UUID sellOrderId = fill.isBuyerIsTaker() ? fill.getMakerOrderId() : fill.getTakerOrderId();

            if (tradeRepository.existsByBuyOrderIdAndSellOrderId(buyOrderId, sellOrderId)) {
                tradeRepository.findFirstByBuyOrderIdAndSellOrderId(buyOrderId, sellOrderId)
                        .ifPresent(trade -> tradeResponses.add(dtoMapper.toTradeResponse(trade)));
                continue;
            }

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
}
