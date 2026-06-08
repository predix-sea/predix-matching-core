package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingCoreHealthMonitorTest {

    @Mock
    private MatchingCoreClient matchingCoreClient;

    @Mock
    private OrderBookWarmupService orderBookWarmupService;

    private MatchingCoreHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new MatchingCoreHealthMonitor(matchingCoreClient, orderBookWarmupService);
    }

    @Test
    void staysHealthy_doesNotWarmup() {
        when(matchingCoreClient.healthCheck()).thenReturn(true);

        monitor.monitorHealth();
        monitor.monitorHealth();

        verify(orderBookWarmupService, never()).warmupAllOpenBooks();
    }

    @Test
    void recoveryAfterOutage_triggersFullWarmup() {
        when(matchingCoreClient.healthCheck()).thenReturn(false, true);

        monitor.monitorHealth();
        monitor.monitorHealth();

        verify(orderBookWarmupService).warmupAllOpenBooks();
    }

    @Test
    void outageWithoutRecovery_doesNotWarmup() {
        when(matchingCoreClient.healthCheck()).thenReturn(true, false);

        monitor.monitorHealth();
        monitor.monitorHealth();

        verify(orderBookWarmupService, never()).warmupAllOpenBooks();
    }
}
