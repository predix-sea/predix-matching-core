package com.predix.matching.client.impl;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.client.dto.CoreMatchResult;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder when gRPC to the C++ core is disabled. Matching is not available in-process.
 */
@Component
@Profile("!h2 & !test")
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableMatchingCoreClient implements MatchingCoreClient {

    private static final String MESSAGE =
            "C++ matching core gRPC is disabled; set predix.matching-core.grpc.enabled=true";

    @Override
    public CoreMatchResult submitOrder(OrderEntity order) {
        throw unavailable();
    }

    @Override
    public boolean cancelOrder(OrderEntity order) {
        throw unavailable();
    }

    @Override
    public List<CoreMatchResult.CoreDepthLevel> getDepth(String marketId, String outcomeId, int levels) {
        throw unavailable();
    }

    @Override
    public int warmupBook(String marketId, String outcomeId, List<CoreMatchResult.CoreBookOrder> orders,
                          boolean replaceExisting) {
        throw unavailable();
    }

    @Override
    public boolean resetBook(String marketId, String outcomeId) {
        throw unavailable();
    }

    @Override
    public boolean healthCheck() {
        return false;
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.MATCHING_CORE_UNAVAILABLE, MESSAGE);
    }
}
