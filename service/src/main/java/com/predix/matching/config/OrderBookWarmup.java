package com.predix.matching.config;

import com.predix.matching.service.OrderBookWarmupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!h2")
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderBookWarmup implements ApplicationRunner {

    private final OrderBookWarmupService orderBookWarmupService;

    @Override
    public void run(ApplicationArguments args) {
        orderBookWarmupService.warmupAllOpenBooks();
    }
}
