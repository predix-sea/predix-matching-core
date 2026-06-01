package com.predix.matching.client;

import com.predix.matching.client.dto.MarketInfoDto;
import com.predix.matching.domain.enums.MarketStatus;

public interface MarketSchemaClient {

    MarketInfoDto getMarket(String marketId);

    void validateOutcome(String marketId, String outcomeId);

    boolean isTradingAllowed(MarketStatus status);
}
