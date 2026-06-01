package com.predix.matching.controller.dto;

import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceOrderRequest {

    @NotBlank
    private String marketId;

    @NotBlank
    private String outcomeId;

    @NotBlank
    private String userId;

    @NotNull
    private OrderSide side;

    @NotNull
    private OrderType orderType;

    @DecimalMin(value = "0.01", inclusive = true, message = "price must be >= 0.01 for limit orders")
    private BigDecimal price;

    @NotNull
    @DecimalMin(value = "0.00000001", inclusive = false, message = "quantity must be > 0")
    private BigDecimal quantity;

    @NotBlank
    private String clientOrderId;
}
