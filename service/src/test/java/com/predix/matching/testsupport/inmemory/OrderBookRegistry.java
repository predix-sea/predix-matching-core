package com.predix.matching.testsupport.inmemory;

import java.util.concurrent.ConcurrentHashMap;

class OrderBookRegistry {

    private final ConcurrentHashMap<String, InMemoryOrderBook> books = new ConcurrentHashMap<>();

    InMemoryOrderBook getOrCreate(String marketId, String outcomeId) {
        return books.computeIfAbsent(marketId + ":" + outcomeId, k -> new InMemoryOrderBook());
    }

    InMemoryOrderBook get(String marketId, String outcomeId) {
        return books.get(marketId + ":" + outcomeId);
    }

    void reset(String marketId, String outcomeId) {
        books.remove(marketId + ":" + outcomeId);
    }
}
