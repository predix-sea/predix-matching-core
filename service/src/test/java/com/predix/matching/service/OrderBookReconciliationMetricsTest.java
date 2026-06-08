package com.predix.matching.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookReconciliationMetricsTest {

    @Test
    void recordsDriftCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderBookReconciliationMetrics metrics = new OrderBookReconciliationMetrics(registry);

        metrics.recordDriftDetected();
        metrics.recordDriftRepaired();
        metrics.recordDriftDetected();

        assertThat(registry.get("predix.orderbook.drift.detected").counter().count()).isEqualTo(2);
        assertThat(registry.get("predix.orderbook.drift.repaired").counter().count()).isEqualTo(1);
    }
}
