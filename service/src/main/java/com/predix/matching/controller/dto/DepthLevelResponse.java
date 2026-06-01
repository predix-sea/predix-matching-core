package com.predix.matching.controller.dto;

import com.predix.matching.domain.enums.OrderSide;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class DepthLevelResponse {
    OrderSide side;
    BigDecimal price;
    BigDecimal quantity;
}
