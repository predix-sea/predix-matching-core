package com.predix.matching.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderBookRegistry {

    private final ConcurrentHashMap<String, InMemoryOrderBook> books = new ConcurrentHashMap<>();

    public InMemoryOrderBook getOrCreate(String marketId, String outcomeId) {
        String key = key(marketId, outcomeId);
        return books.computeIfAbsent(key, k -> new InMemoryOrderBook(marketId, outcomeId));
    }

    public InMemoryOrderBook get(String marketId, String outcomeId) {
        return books.get(key(marketId, outcomeId));
    }

    private static String key(String marketId, String outcomeId) {
        return marketId + ":" + outcomeId;
    }
}
