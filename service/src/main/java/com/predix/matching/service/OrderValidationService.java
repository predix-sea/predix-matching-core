package com.predix.matching.service;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final PredixProperties properties;

    public void validate(PlaceOrderRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "quantity must be > 0");
        }
        if (request.getOrderType() == OrderType.LIMIT) {
            if (request.getPrice() == null) {
                throw new BusinessException(ErrorCode.ORDER_INVALID_PRICE, "Limit order requires price");
            }
            BigDecimal min = properties.getMatching().getPriceMin();
            BigDecimal max = properties.getMatching().getPriceMax();
            if (request.getPrice().compareTo(min) < 0 || request.getPrice().compareTo(max) > 0) {
                throw new BusinessException(ErrorCode.ORDER_INVALID_PRICE,
                        "Price must be between " + min + " and " + max);
            }
        }
    }
}
