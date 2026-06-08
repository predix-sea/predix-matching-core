package com.predix.matching.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderBookReconciliationMetrics {

    private final Counter driftDetected;
    private final Counter driftRepaired;

    public OrderBookReconciliationMetrics(MeterRegistry registry) {
        this.driftDetected = Counter.builder("predix.orderbook.drift.detected")
                .description("Times depth drift was detected between DB and C++")
                .register(registry);
        this.driftRepaired = Counter.builder("predix.orderbook.drift.repaired")
                .description("Times drift was repaired via DB warmup")
                .register(registry);
    }

    public void recordDriftDetected() {
        driftDetected.increment();
    }

    public void recordDriftRepaired() {
        driftRepaired.increment();
    }
}

