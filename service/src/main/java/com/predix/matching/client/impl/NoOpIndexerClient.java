package com.predix.matching.client.impl;

import com.predix.matching.client.IndexerClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "predix.clients.indexer.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpIndexerClient implements IndexerClient {

    @Override
    public Optional<String> findTradeByCode(String tradeCode) {
        return Optional.empty();
    }
}
