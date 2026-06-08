package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.controller.dto.DepthLevelResponse;
import com.predix.matching.controller.dto.OrderBookResponse;
import com.predix.matching.domain.entity.OrderBookEntity;
import com.predix.matching.domain.enums.OrderBookStatus;
import com.predix.matching.repository.OrderBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderBookService {

    private static final int DEFAULT_DEPTH_LEVELS = 10;

    private final OrderBookRepository orderBookRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final DtoMapper dtoMapper;
    private final MarketLifecycleService marketLifecycleService;

    @Transactional
    public OrderBookEntity ensureOrderBook(String marketId, String outcomeId) {
        return orderBookRepository.findByMarketIdAndOutcomeId(marketId, outcomeId)
                .orElseGet(() -> {
                    OrderBookEntity entity = OrderBookEntity.builder()
                            .marketId(marketId)
                            .outcomeId(outcomeId)
                            .status(OrderBookStatus.ACTIVE)
                            .build();
                    return orderBookRepository.save(entity);
                });
    }

    public OrderBookResponse getOrderBook(String marketId, String outcomeId) {
        marketLifecycleService.validateForQuery(marketId);
        OrderBookEntity entity = ensureOrderBook(marketId, outcomeId);
        List<DepthLevelResponse> depth = matchingCoreClient.getDepth(marketId, outcomeId, DEFAULT_DEPTH_LEVELS)
                .stream()
                .map(dtoMapper::toDepth)
                .toList();
        return OrderBookResponse.builder()
                .marketId(entity.getMarketId())
                .outcomeId(entity.getOutcomeId())
                .status(entity.getStatus())
                .updatedAt(entity.getUpdatedAt())
                .depth(depth)
                .build();
    }

    public List<DepthLevelResponse> getDepth(String marketId, String outcomeId, int levels) {
        marketLifecycleService.validateForQuery(marketId);
        ensureOrderBook(marketId, outcomeId);
        return matchingCoreClient.getDepth(marketId, outcomeId, levels).stream()
                .map(dtoMapper::toDepth)
                .toList();
    }
}
