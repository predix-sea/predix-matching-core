package com.predix.matching.client.dto;

import com.predix.matching.domain.enums.MarketStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MarketInfoDto {
    String marketId;
    MarketStatus status;
    List<String> outcomeIds;
}
