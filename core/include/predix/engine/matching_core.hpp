#pragma once

#include "predix/engine/book_registry.hpp"
#include "predix/wal/wal_writer.hpp"

#include <memory>
#include <mutex>
#include <vector>

namespace predix::engine {

class MatchingCore {
public:
    explicit MatchingCore(std::size_t shard_count = 4,
                          std::shared_ptr<wal::WalWriter> wal = nullptr)
        : shard_count_(shard_count == 0 ? 1 : shard_count), wal_(std::move(wal)) {
        shards_.reserve(shard_count_);
        for (std::size_t i = 0; i < shard_count_; ++i) {
            shards_.push_back(std::make_unique<Shard>());
        }
    }

    MatchResult submitOrder(const std::string& market_id, const std::string& outcome_id,
                            BookOrder order) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        if (wal_) {
            wal_->appendSubmit(market_id, outcome_id, order);
        }
        auto& book = shard.registry.getOrCreate(market_id, outcome_id);
        return book.match(std::move(order));
    }

    bool cancelOrder(const std::string& market_id, const std::string& outcome_id,
                     const std::string& order_id) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        if (wal_) {
            wal_->appendCancel(market_id, outcome_id, order_id);
        }
        OrderBook* book = shard.registry.get(market_id, outcome_id);
        return book != nullptr && book->removeFromBook(order_id);
    }

    std::vector<DepthLevel> getDepth(const std::string& market_id, const std::string& outcome_id,
                                     int levels) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        OrderBook* book = shard.registry.get(market_id, outcome_id);
        if (book == nullptr) {
            return {};
        }
        return book->getDepth(levels);
    }

    int warmupBook(const std::string& market_id, const std::string& outcome_id,
                   std::vector<BookOrder> orders) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        auto& book = shard.registry.getOrCreate(market_id, outcome_id);
        int count = 0;
        for (auto& order : orders) {
            book.addToBook(std::move(order));
            ++count;
        }
        return count;
    }

    std::size_t shardCount() const { return shard_count_; }

private:
    struct Shard {
        std::mutex mutex;
        BookRegistry registry;
    };

    Shard& shardFor(const std::string& market_id, const std::string& outcome_id) {
        const std::size_t h = std::hash<std::string>{}(market_id + ":" + outcome_id);
        return *shards_[h % shard_count_];
    }

    std::size_t shard_count_;
    std::vector<std::unique_ptr<Shard>> shards_;
    std::shared_ptr<wal::WalWriter> wal_;
};

}  // namespace predix::engine
