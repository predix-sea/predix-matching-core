package com.predix.matching.client.impl;

import com.predix.matching.client.MarketSchemaClient;
import com.predix.matching.client.dto.MarketInfoDto;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.enums.MarketStatus;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "predix.clients.market-schema.mock", havingValue = "false")
public class HttpMarketSchemaClient implements MarketSchemaClient {

    private final RestTemplate restTemplate;
    private final PredixProperties properties;

    @Override
    @SuppressWarnings("unchecked")
    public MarketInfoDto getMarket(String marketId) {
        String url = properties.getClients().getMarketSchema().getBaseUrl() + "/api/v1/markets/" + marketId;
        try {
            Map<String, Object> body = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Market not found: " + marketId);
            }
            Map<String, Object> data = (Map<String, Object>) body.getOrDefault("data", body);
            return MarketInfoDto.builder()
                    .marketId(marketId)
                    .status(MarketStatus.valueOf(String.valueOf(data.get("status"))))
                    .outcomeIds((java.util.List<String>) data.get("outcomeIds"))
                    .build();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.MARKET_SCHEMA_UNAVAILABLE, e.getMessage());
        }
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
}
