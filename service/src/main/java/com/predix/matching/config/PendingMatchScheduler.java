package com.predix.matching.config;

import com.predix.matching.service.PendingMatchRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!h2")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "predix.pending-match.enabled", havingValue = "true", matchIfMissing = true)
public class PendingMatchScheduler {

    private final PendingMatchRecoveryService pendingMatchRecoveryService;

    @Scheduled(fixedDelayString = "${predix.pending-match.interval-ms:60000}")
    public void recoverPendingMatches() {
        int recovered = pendingMatchRecoveryService.recoverPendingMatches();
        if (recovered > 0) {
            log.warn("Recovered {} orders stuck in PENDING_MATCH", recovered);
        }
    }
}
