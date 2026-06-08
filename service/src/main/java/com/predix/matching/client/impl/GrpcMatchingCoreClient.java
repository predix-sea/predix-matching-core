package com.predix.matching.client.impl;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.client.grpc.GrpcDecimalConverter;
import com.predix.matching.client.grpc.GrpcStatusHelper;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.grpc.BookOrderInput;
import com.predix.matching.grpc.CancelOrderRequest;
import com.predix.matching.grpc.GetDepthRequest;
import com.predix.matching.grpc.HealthRequest;
import com.predix.matching.grpc.MatchingCoreGrpc;
import com.predix.matching.grpc.ResetBookRequest;
import com.predix.matching.grpc.SubmitOrderRequest;
import com.predix.matching.grpc.SubmitOrderResponse;
import com.predix.matching.grpc.WarmupBookRequest;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GrpcMatchingCoreClient implements MatchingCoreClient {

    private final MatchingCoreGrpc.MatchingCoreBlockingStub stub;
    private final PredixProperties properties;

    private MatchingCoreGrpc.MatchingCoreBlockingStub callStub() {
        return stub.withDeadlineAfter(properties.getMatchingCore().getGrpc().getDeadlineMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public CoreMatchResult submitOrder(OrderEntity order) {
        try {
            BookOrderInput bookOrder = toBookOrderInput(order);
            SubmitOrderResponse response = callStub().submitOrder(SubmitOrderRequest.newBuilder()
                    .setMarketId(order.getMarketId())
                    .setOutcomeId(order.getOutcomeId())
                    .setOrder(bookOrder)
                    .build());
            return toCoreResult(order.getId(), response);
        } catch (StatusRuntimeException e) {
            throw GrpcStatusHelper.toBusinessException("submitOrder", e);
        }
    }

    @Override
    public boolean cancelOrder(OrderEntity order) {
        try {
            return callStub().cancelOrder(CancelOrderRequest.newBuilder()
                    .setMarketId(order.getMarketId())
                    .setOutcomeId(order.getOutcomeId())
                    .setOrderId(order.getId().toString())
                    .build()).getRemoved();
        } catch (StatusRuntimeException e) {
            throw GrpcStatusHelper.toBusinessException("cancelOrder", e);
        }
    }

    @Override
    public List<CoreMatchResult.CoreDepthLevel> getDepth(String marketId, String outcomeId, int levels) {
        try {
            return callStub().getDepth(GetDepthRequest.newBuilder()
                    .setMarketId(marketId)
                    .setOutcomeId(outcomeId)
                    .setLevels(levels)
                    .build()).getLevelsList().stream()
                    .map(level -> CoreMatchResult.CoreDepthLevel.builder()
                            .side(GrpcDecimalConverter.fromGrpcSide(level.getSide()))
                            .price(GrpcDecimalConverter.fromRaw(level.getPrice()))
                            .quantity(GrpcDecimalConverter.fromRaw(level.getQuantity()))
                            .build())
                    .collect(Collectors.toList());
        } catch (StatusRuntimeException e) {
            throw GrpcStatusHelper.toBusinessException("getDepth", e);
        }
    }

    @Override
    public int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders,
                          boolean replaceExisting) {
        try {
            WarmupBookRequest.Builder builder = WarmupBookRequest.newBuilder()
                    .setMarketId(marketId)
                    .setOutcomeId(outcomeId)
                    .setReplaceExisting(replaceExisting);
            for (CoreMatchResult.CoreBookOrder order : orders) {
                builder.addOrders(toBookOrderInput(order));
            }
            return callStub().warmupBook(builder.build()).getLoadedCount();
        } catch (StatusRuntimeException e) {
            throw GrpcStatusHelper.toBusinessException("warmupBook", e);
        }
    }

    @Override
    public boolean resetBook(String marketId, String outcomeId) {
        try {
            return callStub().resetBook(ResetBookRequest.newBuilder()
                    .setMarketId(marketId)
                    .setOutcomeId(outcomeId)
                    .build()).getReset();
        } catch (StatusRuntimeException e) {
            throw GrpcStatusHelper.toBusinessException("resetBook", e);
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            return callStub().health(HealthRequest.getDefaultInstance()).getHealthy();
        } catch (StatusRuntimeException e) {
            log.warn("gRPC healthCheck failed: {}", e.getStatus());
            return false;
        }
    }

    private CoreMatchResult toCoreResult(UUID orderId, SubmitOrderResponse response) {
        List<CoreMatchResult.CoreTradeFill> fills = response.getFillsList().stream()
                .map(fill -> CoreMatchResult.CoreTradeFill.builder()
                        .makerOrderId(UUID.fromString(fill.getMakerOrderId()))
                        .makerUserId(fill.getMakerUserId())
                        .takerOrderId(UUID.fromString(fill.getTakerOrderId()))
                        .takerUserId(fill.getTakerUserId())
                        .price(GrpcDecimalConverter.fromRaw(fill.getPrice()))
                        .quantity(GrpcDecimalConverter.fromRaw(fill.getQuantity()))
                        .buyerIsTaker(fill.getBuyerIsTaker())
                        .build())
                .collect(Collectors.toList());

        return CoreMatchResult.builder()
                .orderId(orderId)
                .remainingQuantity(GrpcDecimalConverter.fromRaw(response.getIncomingOrder().getRemainingQuantity()))
                .fullyFilled(response.getFullyFilled())
                .rejected(response.getRejected())
                .rejectReason(response.getRejectReason())
                .fills(fills)
                .build();
    }

    private BookOrderInput toBookOrderInput(OrderEntity order) {
        return BookOrderInput.newBuilder()
                .setOrderId(order.getId().toString())
                .setUserId(order.getUserId())
                .setSide(GrpcDecimalConverter.toGrpcSide(order.getSide()))
                .setOrderType(GrpcDecimalConverter.toGrpcOrderType(order.getOrderType()))
                .setPrice(GrpcDecimalConverter.toRaw(order.getPrice()))
                .setRemainingQuantity(GrpcDecimalConverter.toRaw(order.getRemainingQuantity()))
                .setCreatedAtMs(order.getCreatedAt() != null ? order.getCreatedAt().toEpochMilli() : System.currentTimeMillis())
                .setSequence(0)
                .build();
    }

    private BookOrderInput toBookOrderInput(CoreMatchResult.CoreBookOrder order) {
        return BookOrderInput.newBuilder()
                .setOrderId(order.getId().toString())
                .setUserId(order.getUserId())
                .setSide(GrpcDecimalConverter.toGrpcSide(order.getSide()))
                .setOrderType(GrpcDecimalConverter.toGrpcOrderType(order.getOrderType()))
                .setPrice(GrpcDecimalConverter.toRaw(order.getPrice()))
                .setRemainingQuantity(GrpcDecimalConverter.toRaw(order.getRemainingQuantity()))
                .setCreatedAtMs(order.getCreatedAtEpochMs())
                .setSequence(order.getSequence())
                .build();
    }
}
