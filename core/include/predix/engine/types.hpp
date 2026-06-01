#pragma once

#include "predix/engine/decimal.hpp"
#include <cstdint>
#include <string>
#include <vector>

namespace predix::engine {

enum class Side { BUY, SELL };
enum class OrderType { LIMIT, MARKET };

struct BookOrder {
    std::string id;
    std::string user_id;
    Side side;
    OrderType order_type;
    Decimal price;
    Decimal remaining_quantity;
    int64_t created_at_epoch_ms{0};
    int64_t sequence{0};
};

struct TradeFill {
    std::string maker_order_id;
    std::string maker_user_id;
    std::string taker_order_id;
    std::string taker_user_id;
    Decimal price;
    Decimal quantity;
    bool buyer_is_taker{false};
};

struct MatchResult {
    BookOrder incoming_order;
    std::vector<TradeFill> fills;
    bool fully_filled{false};
    bool rejected{false};
    std::string reject_reason;
};

struct DepthLevel {
    Side side;
    Decimal price;
    Decimal quantity;
};

}  // namespace predix::engine
