package com.predix.matching.config;

import com.predix.matching.config.PredixProperties;
import com.predix.matching.grpc.MatchingCoreGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "predix.matching-core.grpc.enabled", havingValue = "true")
public class MatchingCoreGrpcConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel matchingCoreChannel(PredixProperties properties) throws Exception {
        var grpc = properties.getMatchingCore().getGrpc();
        log.info("Connecting to C++ matching core at {}:{} (tls={})", grpc.getHost(), grpc.getPort(), grpc.isTlsEnabled());

        if (grpc.isTlsEnabled()) {
            if (grpc.getTrustCertPath() == null || grpc.getTrustCertPath().isBlank()) {
                throw new IllegalStateException("predix.matching-core.grpc.tls-enabled=true requires trust-cert-path");
            }
            var sslContext = GrpcSslContexts.forClient()
                    .trustManager(new File(grpc.getTrustCertPath()))
                    .build();
            return NettyChannelBuilder.forAddress(grpc.getHost(), grpc.getPort())
                    .sslContext(sslContext)
                    .build();
        }

        return NettyChannelBuilder.forAddress(grpc.getHost(), grpc.getPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public MatchingCoreGrpc.MatchingCoreBlockingStub matchingCoreStub(ManagedChannel matchingCoreChannel) {
        return MatchingCoreGrpc.newBlockingStub(matchingCoreChannel);
    }
}
