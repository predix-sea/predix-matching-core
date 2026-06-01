package com.predix.matching.client;

import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;

import java.util.List;

public interface MatchingCoreClient {

    CoreMatchResult submitOrder(OrderEntity order);

    boolean cancelOrder(OrderEntity order);

    List<CoreMatchResult.CoreDepthLevel> getDepth(String marketId, String outcomeId, int levels);

    int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders);

    boolean healthCheck();
}
