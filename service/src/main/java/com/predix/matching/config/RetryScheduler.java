package com.predix.matching.config;

import com.predix.matching.service.ExecutionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!h2")
@RequiredArgsConstructor
public class RetryScheduler {

    private final ExecutionTaskService executionTaskService;

    @Scheduled(fixedDelayString = "${predix.matching.retry-scheduler-ms:30000}")
    public void processRetries() {
        executionTaskService.processDueRetries();
    }
}
