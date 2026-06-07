package com.predix.matching.config;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.grpc.MatchingCoreGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "true")
public class MatchingCoreGrpcConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel matchingCoreChannel(PredixProperties properties) {
        var grpc = properties.getMatchingCore().getGrpc();
        log.info("Connecting to C++ matching core at {}:{}", grpc.getHost(), grpc.getPort());
        return ManagedChannelBuilder.forAddress(grpc.getHost(), grpc.getPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public MatchingCoreGrpc.MatchingCoreBlockingStub matchingCoreStub(
            ManagedChannel matchingCoreChannel, PredixProperties properties) {
        long deadlineMs = properties.getMatchingCore().getGrpc().getDeadlineMs();
        return MatchingCoreGrpc.newBlockingStub(matchingCoreChannel)
                .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }
}
