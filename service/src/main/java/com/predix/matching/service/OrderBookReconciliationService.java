package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderBookEntity;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderBookStatus;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.repository.OrderBookRepository;
import com.predix.matching.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookReconciliationService {

    private final OrderBookRepository orderBookRepository;
    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final OrderBookWarmupService orderBookWarmupService;
    private final PredixProperties properties;

    public int reconcileAllActiveBooks() {
        List<OrderBookEntity> books = orderBookRepository.findAll().stream()
                .filter(b -> b.getStatus() == OrderBookStatus.ACTIVE)
                .toList();
        int driftCount = 0;
        for (OrderBookEntity book : books) {
            if (reconcileBook(book.getMarketId(), book.getOutcomeId())) {
                driftCount++;
            }
        }
        return driftCount;
    }

    public boolean reconcileBook(String marketId, String outcomeId) {
        int levels = properties.getReconciliation().getDepthLevels();
        List<CoreMatchResult.CoreDepthLevel> dbDepth = computeDepthFromDb(marketId, outcomeId, levels);
        List<CoreMatchResult.CoreDepthLevel> coreDepth =
                matchingCoreClient.getDepth(marketId, outcomeId, levels);

        if (depthMatches(dbDepth, coreDepth)) {
            return false;
        }

        log.warn("Order book drift detected marketId={} outcomeId={} dbLevels={} coreLevels={}",
                marketId, outcomeId, dbDepth.size(), coreDepth.size());
        orderBookWarmupService.warmupBookFromDb(marketId, outcomeId);
        return true;
    }

    List<CoreMatchResult.CoreDepthLevel> computeDepthFromDb(String marketId, String outcomeId, int levels) {
        List<OrderEntity> openOrders = orderRepository.findOpenOrders(marketId, outcomeId);
        Map<String, CoreMatchResult.CoreDepthLevel> aggregated = new HashMap<>();

        for (OrderEntity order : openOrders) {
            if (order.getPrice() == null) {
                continue;
            }
            String key = order.getSide().name() + ":" + order.getPrice().setScale(8, RoundingMode.HALF_UP).toPlainString();
            aggregated.merge(key,
                    CoreMatchResult.CoreDepthLevel.builder()
                            .side(order.getSide())
                            .price(order.getPrice())
                            .quantity(order.getRemainingQuantity())
                            .build(),
                    (left, right) -> CoreMatchResult.CoreDepthLevel.builder()
                            .side(left.getSide())
                            .price(left.getPrice())
                            .quantity(left.getQuantity().add(right.getQuantity()))
                            .build());
        }

        List<CoreMatchResult.CoreDepthLevel> bids = aggregated.values().stream()
                .filter(level -> level.getSide() == OrderSide.BUY)
                .sorted(Comparator.comparing(CoreMatchResult.CoreDepthLevel::getPrice).reversed())
                .limit(levels)
                .toList();
        List<CoreMatchResult.CoreDepthLevel> asks = aggregated.values().stream()
                .filter(level -> level.getSide() == OrderSide.SELL)
                .sorted(Comparator.comparing(CoreMatchResult.CoreDepthLevel::getPrice))
                .limit(levels)
                .toList();

        List<CoreMatchResult.CoreDepthLevel> result = new ArrayList<>(bids.size() + asks.size());
        result.addAll(bids);
        result.addAll(asks);
        return result;
    }

    private static boolean depthMatches(List<CoreMatchResult.CoreDepthLevel> dbDepth,
                                        List<CoreMatchResult.CoreDepthLevel> coreDepth) {
        return toDepthMap(dbDepth).equals(toDepthMap(coreDepth));
    }

    private static Map<String, BigDecimal> toDepthMap(List<CoreMatchResult.CoreDepthLevel> depth) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (CoreMatchResult.CoreDepthLevel level : depth) {
            map.put(level.getSide().name() + ":" + scale(level.getPrice()), scale(level.getQuantity()));
        }
        return map;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(8, RoundingMode.HALF_UP);
    }
}
