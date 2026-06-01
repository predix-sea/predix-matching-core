package com.predix.matching.service;

import com.predix.matching.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancelUnitTest {

    @Test
    void cancellableStatuses() {
        assertThat(OrderStatus.NEW.canCancel()).isTrue();
        assertThat(OrderStatus.PARTIAL.canCancel()).isTrue();
        assertThat(OrderStatus.FILLED.canCancel()).isFalse();
        assertThat(OrderStatus.CANCELLED.isFinal()).isTrue();
    }
}
