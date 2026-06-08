package com.predix.matching.config;

import com.predix.matching.service.PendingCancelRecoveryService;
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
@ConditionalOnProperty(name = "predix.pending-cancel.enabled", havingValue = "true", matchIfMissing = true)
public class PendingCancelScheduler {

    private final PendingCancelRecoveryService pendingCancelRecoveryService;

    @Scheduled(fixedDelayString = "${predix.pending-cancel.interval-ms:60000}")
    public void recoverPendingCancels() {
        int recovered = pendingCancelRecoveryService.recoverPendingCancels();
        if (recovered > 0) {
            log.warn("Recovered {} orders stuck in PENDING_CANCEL", recovered);
        }
    }
}
