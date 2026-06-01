#pragma once

#include "predix/engine/price_level.hpp"
#include "predix/engine/types.hpp"

#include <atomic>
#include <map>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace predix::engine {

using BidBook = std::map<Decimal, PriceLevel, std::greater<Decimal>>;
using AskBook = std::map<Decimal, PriceLevel>;

class OrderBook {
public:
    OrderBook(std::string market_id, std::string outcome_id)
        : market_id_(std::move(market_id)), outcome_id_(std::move(outcome_id)) {}

    const std::string& marketId() const { return market_id_; }
    const std::string& outcomeId() const { return outcome_id_; }

    MatchResult match(BookOrder incoming) {
        if (incoming.side == Side::BUY) {
            return matchAgainst(incoming, asks_);
        }
        return matchAgainst(incoming, bids_);
    }

    void addToBook(BookOrder order) {
        if (!order.remaining_quantity.isPositive()) {
            return;
        }
        BookOrder with_seq = std::move(order);
        with_seq.sequence = sequence_++ + 1;
        if (with_seq.side == Side::BUY) {
            addToSide(bids_, with_seq);
        } else {
            addToSide(asks_, with_seq);
        }
    }

    bool removeFromBook(const std::string& order_id) {
        auto idx = order_index_.find(order_id);
        if (idx == order_index_.end()) {
            return false;
        }
        const BookOrder order = idx->second;
        order_index_.erase(idx);
        if (order.side == Side::BUY) {
            return removeFromSide(bids_, order);
        }
        return removeFromSide(asks_, order);
    }

    std::vector<DepthLevel> getDepth(int levels) const {
        std::vector<DepthLevel> result;
        int count = 0;
        for (const auto& [price, level] : bids_) {
            if (count++ >= levels) break;
            result.push_back({Side::BUY, price, level.totalQuantity()});
        }
        count = 0;
        for (const auto& [price, level] : asks_) {
            if (count++ >= levels) break;
            result.push_back({Side::SELL, price, level.totalQuantity()});
        }
        return result;
    }

private:
    template <typename BookSide>
    MatchResult matchAgainst(BookOrder incoming, BookSide& opposite) {
        std::vector<TradeFill> fills;
        Decimal remaining = incoming.remaining_quantity;

        while (remaining.isPositive() && !opposite.empty()) {
            auto best_it = opposite.begin();
            const Decimal best_price = best_it->first;
            if (!canMatchPrice(incoming, best_price)) {
                break;
            }

            PriceLevel& level = best_it->second;
            while (remaining.isPositive() && !level.empty()) {
                BookOrder maker = level.poll();
                if (maker.id.empty()) {
                    break;
                }

                const Decimal fill_qty = remaining.min(maker.remaining_quantity);
                const bool buyer_is_taker = incoming.side == Side::BUY;

                fills.push_back(TradeFill{
                    maker.id, maker.user_id, incoming.id, incoming.user_id,
                    maker.price, fill_qty, buyer_is_taker,
                });

                remaining = remaining - fill_qty;
                const Decimal maker_remaining = maker.remaining_quantity - fill_qty;

                if (!maker_remaining.isPositive()) {
                    order_index_.erase(maker.id);
                } else {
                    maker.remaining_quantity = maker_remaining;
                    level.requeueFront(std::move(maker));
                    if (const BookOrder* peeked = level.peek()) {
                        order_index_[peeked->id] = *peeked;
                    }
                }
            }

            if (level.empty()) {
                opposite.erase(best_it);
            }
        }

        BookOrder updated = incoming;
        updated.remaining_quantity = remaining;
        const bool fully_filled = !remaining.isPositive();
        bool rejected = false;
        std::string reject_reason;

        if (!fully_filled) {
            if (incoming.order_type == OrderType::MARKET) {
                if (fills.empty()) {
                    rejected = true;
                    reject_reason = "No liquidity for market order";
                }
            } else {
                addToBook(BookOrder{updated});
            }
        }

        return MatchResult{std::move(updated), std::move(fills), fully_filled, rejected, reject_reason};
    }

    template <typename BookSide>
    void addToSide(BookSide& side, BookOrder with_seq) {
        auto [level_it, inserted] = side.try_emplace(with_seq.price, PriceLevel(with_seq.price));
        level_it->second.add(with_seq);
        order_index_[with_seq.id] = with_seq;
    }

    template <typename BookSide>
    bool removeFromSide(BookSide& side, const BookOrder& order) {
        auto level_it = side.find(order.price);
        if (level_it == side.end()) {
            return false;
        }
        PriceLevel& level = level_it->second;
        std::deque<BookOrder> temp;
        bool found = false;
        while (!level.empty()) {
            BookOrder o = level.poll();
            if (o.id == order.id) {
                found = true;
                break;
            }
            temp.push_back(std::move(o));
        }
        for (auto& o : temp) {
            level.add(std::move(o));
        }
        if (level.empty()) {
            side.erase(level_it);
        }
        return found;
    }

    bool canMatchPrice(const BookOrder& incoming, Decimal opposite_price) const {
        if (incoming.order_type == OrderType::MARKET) {
            return true;
        }
        if (incoming.side == Side::BUY) {
            return incoming.price >= opposite_price;
        }
        return incoming.price <= opposite_price;
    }

    std::string market_id_;
    std::string outcome_id_;
    BidBook bids_;
    AskBook asks_;
    int64_t sequence_{0};
    std::unordered_map<std::string, BookOrder> order_index_;
};

}  // namespace predix::engine
