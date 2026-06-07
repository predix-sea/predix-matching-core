package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Profile("!h2")
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MatchingCoreHealthMonitor {

    private final MatchingCoreClient matchingCoreClient;
    private final OrderBookWarmupService orderBookWarmupService;
    private final AtomicBoolean lastHealthy = new AtomicBoolean(true);

    @Scheduled(fixedDelayString = "${predix.matching-core.health-check-ms:30000}")
    public void monitorHealth() {
        boolean healthy = matchingCoreClient.healthCheck();
        boolean wasHealthy = lastHealthy.getAndSet(healthy);
        if (!wasHealthy && healthy) {
            log.warn("Matching core recovered; reloading all open books from database");
            orderBookWarmupService.warmupAllOpenBooks();
        } else if (wasHealthy && !healthy) {
            log.error("Matching core became unhealthy");
        }
    }
}
