package com.predix.matching.service;

import com.predix.matching.client.MarketSchemaClient;
import com.predix.matching.client.dto.MarketInfoDto;
import com.predix.matching.domain.enums.MarketStatus;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketLifecycleService {

    private final MarketSchemaClient marketSchemaClient;

    public MarketInfoDto validateForPlaceOrder(String marketId, String outcomeId) {
        MarketInfoDto market = marketSchemaClient.getMarket(marketId);
        if (!marketSchemaClient.isTradingAllowed(market.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_MARKET_STATUS,
                    "Market " + marketId + " status " + market.getStatus() + " does not allow trading");
        }
        marketSchemaClient.validateOutcome(marketId, outcomeId);
        return market;
    }

    public void validateForCancel(String marketId) {
        MarketInfoDto market = marketSchemaClient.getMarket(marketId);
        if (market.getStatus() == MarketStatus.RESOLVING || market.getStatus() == MarketStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_MARKET_STATUS,
                    "Cannot cancel orders in resolving/resolved market");
        }
    }

    public void validateForQuery(String marketId) {
        marketSchemaClient.getMarket(marketId);
    }
}
