package com.predix.matching.controller.dto;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successIncludesTraceId() {
        MDC.put("traceId", "trace-123");
        ApiResponse<String> response = ApiResponse.success("ok");
        assertThat(response.getCode()).isEqualTo("OK");
        assertThat(response.getTraceId()).isEqualTo("trace-123");
        assertThat(response.getData()).isEqualTo("ok");
        MDC.clear();
    }
}
