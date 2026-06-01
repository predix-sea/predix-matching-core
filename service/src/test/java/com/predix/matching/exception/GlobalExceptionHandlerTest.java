package com.predix.matching.exception;

import com.predix.matching.controller.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException(ErrorCode.ORDER_INVALID_PRICE));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("ORDER_INVALID_PRICE");
    }
}
