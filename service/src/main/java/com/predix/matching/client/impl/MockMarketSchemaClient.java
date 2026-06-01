package com.predix.matching.client.impl;

import com.predix.matching.client.MarketSchemaClient;
import com.predix.matching.client.dto.MarketInfoDto;
import com.predix.matching.domain.enums.MarketStatus;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "predix.clients.market-schema.mock", havingValue = "true", matchIfMissing = true)
public class MockMarketSchemaClient implements MarketSchemaClient {

    private final Map<String, MarketInfoDto> markets = new ConcurrentHashMap<>();

    public MockMarketSchemaClient() {
        markets.put("mkt-demo", MarketInfoDto.builder()
                .marketId("mkt-demo")
                .status(MarketStatus.OPEN)
                .outcomeIds(List.of("yes", "no"))
                .build());
    }

    @Override
    public MarketInfoDto getMarket(String marketId) {
        MarketInfoDto market = markets.get(marketId);
        if (market == null) {
            markets.putIfAbsent(marketId, MarketInfoDto.builder()
                    .marketId(marketId)
                    .status(MarketStatus.OPEN)
                    .outcomeIds(List.of("yes", "no"))
                    .build());
            market = markets.get(marketId);
        }
        return market;
    }

    @Override
    public void validateOutcome(String marketId, String outcomeId) {
        MarketInfoDto market = getMarket(marketId);
        if (!market.getOutcomeIds().contains(outcomeId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid outcome: " + outcomeId);
        }
    }

    @Override
    public boolean isTradingAllowed(MarketStatus status) {
        return status == MarketStatus.OPEN;
    }

    public void setMarketStatus(String marketId, MarketStatus status) {
        MarketInfoDto existing = getMarket(marketId);
        markets.put(marketId, MarketInfoDto.builder()
                .marketId(marketId)
                .status(status)
                .outcomeIds(existing.getOutcomeIds())
                .build());
    }
}
