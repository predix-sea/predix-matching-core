package com.predix.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "predix")
public class PredixProperties {

    private Matching matching = new Matching();
    private MatchingCoreConfig matchingCore = new MatchingCoreConfig();
    private Clients clients = new Clients();
    private Mq mq = new Mq();
    private Security security = new Security();

    @Data
    public static class MatchingCoreConfig {
        private Grpc grpc = new Grpc();

        @Data
        public static class Grpc {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 50051;
        }
    }

    @Data
    public static class Matching {
        private BigDecimal priceMin = new BigDecimal("0.01");
        private BigDecimal priceMax = new BigDecimal("0.99");
        private int maxRetryCount = 5;
        private long retryBaseDelayMs = 1000;
    }

    @Data
    public static class Clients {
        private ClientConfig marketSchema = new ClientConfig();
        private ClientConfig ctfGateway = new ClientConfig();
        private IndexerConfig indexer = new IndexerConfig();
    }

    @Data
    public static class ClientConfig {
        private String baseUrl;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private int maxRetries = 2;
    }

    @Data
    public static class IndexerConfig extends ClientConfig {
        private boolean enabled = false;
    }

    @Data
    public static class Mq {
        private String exchange = "predix.matching.exchange";
        private String eventsQueue = "predix.matching.events";
        private String executionQueue = "predix.matching.execution";
        private String dlq = "predix.matching.dlq";
        private int maxRetryAttempts = 5;
    }

    @Data
    public static class Security {
        private boolean trustBffUserHeader = true;
        private String bffUserIdHeader = "X-BFF-User-Id";
        private String signatureHeader = "X-BFF-Signature";
    }
}
