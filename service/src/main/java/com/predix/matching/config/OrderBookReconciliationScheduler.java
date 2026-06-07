package com.predix.matching.config;

import com.predix.matching.service.OrderBookReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!h2")
@RequiredArgsConstructor
@ConditionalOnExpression("${predix.reconciliation.enabled:true} && ${predix.matching-core.grpc.enabled:false}")
public class OrderBookReconciliationScheduler {

    private final OrderBookReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${predix.reconciliation.interval-ms:300000}")
    public void reconcileBooks() {
        int driftCount = reconciliationService.reconcileAllActiveBooks();
        if (driftCount > 0) {
            log.warn("Reconciliation repaired {} drifting order books", driftCount);
        }
    }
}
