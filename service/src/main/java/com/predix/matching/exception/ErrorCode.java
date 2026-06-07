package com.predix.matching.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED),
    ORDER_IDEMPOTENCY_CONFLICT("ORDER_IDEMPOTENCY_CONFLICT", "Idempotency conflict", HttpStatus.CONFLICT),
    ORDER_INVALID_MARKET_STATUS("ORDER_INVALID_MARKET_STATUS", "Market status does not allow this operation", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_PRICE("ORDER_INVALID_PRICE", "Invalid order price", HttpStatus.BAD_REQUEST),
    ORDER_INSUFFICIENT_LIQUIDITY("ORDER_INSUFFICIENT_LIQUIDITY", "Insufficient liquidity for market order", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_FINALIZED("ORDER_ALREADY_FINALIZED", "Order is already in a final state", HttpStatus.CONFLICT),
    ORDER_INVALID_TRANSITION("ORDER_INVALID_TRANSITION", "Invalid order status transition", HttpStatus.CONFLICT),
    EXECUTION_TASK_RETRY_EXCEEDED("EXECUTION_TASK_RETRY_EXCEEDED", "Execution task retry limit exceeded", HttpStatus.CONFLICT),
    EXECUTION_TASK_NOT_FOUND("EXECUTION_TASK_NOT_FOUND", "Execution task not found", HttpStatus.NOT_FOUND),
    MARKET_SCHEMA_UNAVAILABLE("MARKET_SCHEMA_UNAVAILABLE", "Market schema service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    MATCHING_CORE_UNAVAILABLE("MATCHING_CORE_UNAVAILABLE", "Matching core unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
