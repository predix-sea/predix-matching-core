package com.predix.matching.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

public final class TraceContext {

    private static final String TRACE_ID_KEY = "traceId";
    private static final Logger log = LoggerFactory.getLogger(TraceContext.class);

    private TraceContext() {
    }

    public static String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static Optional<TraceContext> current() {
        return Optional.of(new TraceContext());
    }

    public void logError(Exception ex) {
        log.error("traceId={} error={}", getOrCreateTraceId(), ex.getMessage(), ex);
    }
}
