package com.predix.matching.service;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.controller.dto.PlaceOrderRequest;
import com.predix.matching.domain.enums.OrderSide;
import com.predix.matching.domain.enums.OrderType;
import com.predix.matching.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderValidationServiceTest {

    private OrderValidationService validationService;

    @BeforeEach
    void setUp() {
        PredixProperties props = new PredixProperties();
        validationService = new OrderValidationService(props);
    }

    @Test
    void rejectsZeroQuantity() {
        PlaceOrderRequest req = baseRequest();
        req.setQuantity(BigDecimal.ZERO);
        assertThatThrownBy(() -> validationService.validate(req)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsPriceOutOfRange() {
        PlaceOrderRequest req = baseRequest();
        req.setPrice(new BigDecimal("1.50"));
        assertThatThrownBy(() -> validationService.validate(req)).isInstanceOf(BusinessException.class);
    }

    private PlaceOrderRequest baseRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setMarketId("m1");
        req.setOutcomeId("yes");
        req.setUserId("u1");
        req.setSide(OrderSide.BUY);
        req.setOrderType(OrderType.LIMIT);
        req.setPrice(new BigDecimal("0.50"));
        req.setQuantity(new BigDecimal("10"));
        req.setClientOrderId("c1");
        return req;
    }
}
