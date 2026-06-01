package com.predix.matching.client;

import java.util.Optional;

public interface IndexerClient {

    Optional<String> findTradeByCode(String tradeCode);
}
