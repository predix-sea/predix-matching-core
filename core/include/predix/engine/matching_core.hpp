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
                            BookOrder order, bool write_wal = true) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        auto& book = shard.registry.getOrCreate(market_id, outcome_id);

        if (const auto cached = book.findSubmissionResult(order.id)) {
            return *cached;
        }
        if (book.hasRestingOrder(order.id)) {
            return book.restingOrderResult(order.id);
        }

        if (wal_ && write_wal) {
            wal_->appendSubmit(market_id, outcome_id, order);
        }
        MatchResult result = book.match(std::move(order));
        book.recordSubmissionResult(result);
        return result;
    }

    bool cancelOrder(const std::string& market_id, const std::string& outcome_id,
                     const std::string& order_id, bool write_wal = true) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        OrderBook* book = shard.registry.get(market_id, outcome_id);
        if (book == nullptr) {
            return false;
        }
        if (!book->hasRestingOrder(order_id)) {
            return false;
        }
        if (wal_ && write_wal) {
            wal_->appendCancel(market_id, outcome_id, order_id);
        }
        const bool removed = book->removeFromBook(order_id);
        if (removed) {
            book->forgetSubmissionResult(order_id);
        }
        return removed;
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

    bool resetBook(const std::string& market_id, const std::string& outcome_id) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        return shard.registry.reset(market_id, outcome_id);
    }

    int warmupBook(const std::string& market_id, const std::string& outcome_id,
                   std::vector<BookOrder> orders, bool replace_existing = true) {
        Shard& shard = shardFor(market_id, outcome_id);
        std::lock_guard lock(shard.mutex);
        if (replace_existing) {
            shard.registry.reset(market_id, outcome_id);
        }
        auto& book = shard.registry.getOrCreate(market_id, outcome_id);
        int count = 0;
        for (auto& order : orders) {
            if (!book.hasRestingOrder(order.id)) {
                book.addToBook(std::move(order));
                ++count;
            }
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
