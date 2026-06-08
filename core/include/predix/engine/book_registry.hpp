#pragma once

#include "predix/engine/order_book.hpp"
#include <mutex>
#include <string>
#include <unordered_map>

namespace predix::engine {

struct BookKey {
    std::string market_id;
    std::string outcome_id;

    bool operator==(const BookKey& o) const {
        return market_id == o.market_id && outcome_id == o.outcome_id;
    }
};

struct BookKeyHash {
    size_t operator()(const BookKey& k) const {
        return std::hash<std::string>{}(k.market_id) ^ (std::hash<std::string>{}(k.outcome_id) << 1);
    }
};

class BookRegistry {
public:
    OrderBook& getOrCreate(const std::string& market_id, const std::string& outcome_id) {
        BookKey key{market_id, outcome_id};
        std::lock_guard lock(mutex_);
        auto it = books_.find(key);
        if (it == books_.end()) {
            it = books_.emplace(key, OrderBook(market_id, outcome_id)).first;
        }
        return it->second;
    }

    OrderBook* get(const std::string& market_id, const std::string& outcome_id) {
        BookKey key{market_id, outcome_id};
        std::lock_guard lock(mutex_);
        auto it = books_.find(key);
        return it == books_.end() ? nullptr : &it->second;
    }

    bool reset(const std::string& market_id, const std::string& outcome_id) {
        BookKey key{market_id, outcome_id};
        std::lock_guard lock(mutex_);
        return books_.erase(key) > 0;
    }

private:
    std::mutex mutex_;
    std::unordered_map<BookKey, OrderBook, BookKeyHash> books_;
};

}  // namespace predix::engine
