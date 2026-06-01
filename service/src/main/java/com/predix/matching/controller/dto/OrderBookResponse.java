package com.predix.matching.controller.dto;

import com.predix.matching.domain.enums.OrderBookStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class OrderBookResponse {
    String marketId;
    String outcomeId;
    OrderBookStatus status;
    Instant updatedAt;
    List<DepthLevelResponse> depth;
}
