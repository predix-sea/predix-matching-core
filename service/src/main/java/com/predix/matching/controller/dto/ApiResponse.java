package com.predix.matching.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.predix.matching.config.TraceContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code("OK")
                .message("Success")
                .data(data)
                .traceId(TraceContext.getOrCreateTraceId())
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .traceId(TraceContext.getOrCreateTraceId())
                .timestamp(Instant.now())
                .build();
    }
}
