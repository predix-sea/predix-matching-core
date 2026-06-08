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
    private Reconciliation reconciliation = new Reconciliation();
    private Admin admin = new Admin();
    private PendingMatch pendingMatch = new PendingMatch();
    private PendingCancel pendingCancel = new PendingCancel();
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
            private long deadlineMs = 5000;
            private boolean tlsEnabled = false;
            private String trustCertPath;
        }
    }

    @Data
    public static class Admin {
        private boolean enabled = false;
        private String apiKey = "";
    }

    @Data
    public static class PendingMatch {
        private boolean enabled = true;
        private long intervalMs = 60000;
        private int batchSize = 50;
    }

    @Data
    public static class PendingCancel {
        private boolean enabled = true;
        private long intervalMs = 60000;
        private int batchSize = 50;
    }

    @Data
    public static class Reconciliation {
        private boolean enabled = true;
        private long intervalMs = 300000;
        private int depthLevels = 20;
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
