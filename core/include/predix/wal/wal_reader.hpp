#pragma once

#include "predix/engine/matching_core.hpp"

#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace predix::wal {

inline std::vector<std::string> splitWalRecord(const std::string& line, char delimiter) {
    std::vector<std::string> parts;
    std::string part;
    std::istringstream stream(line);
    while (std::getline(stream, part, delimiter)) {
        parts.push_back(part);
    }
    return parts;
}

class WalReader {
public:
    static int replay(const std::string& path, engine::MatchingCore& core) {
        std::ifstream input(path);
        if (!input.is_open()) {
            return 0;
        }

        int applied = 0;
        std::string line;
        while (std::getline(input, line)) {
            if (line.empty()) {
                continue;
            }
            const auto parts = splitWalRecord(line, '|');
            if (parts.empty()) {
                continue;
            }

            if (parts[0] == "SUBMIT" && parts.size() >= 9) {
                engine::BookOrder order;
                order.id = parts[3];
                order.user_id = parts[4];
                order.side = static_cast<engine::Side>(std::stoi(parts[5]));
                order.order_type = static_cast<engine::OrderType>(std::stoi(parts[6]));
                order.price = engine::Decimal::fromString(parts[7]);
                order.remaining_quantity = engine::Decimal::fromString(parts[8]);
                core.submitOrder(parts[1], parts[2], std::move(order), false);
                ++applied;
            } else if (parts[0] == "CANCEL" && parts.size() >= 4) {
                core.cancelOrder(parts[1], parts[2], parts[3], false);
                ++applied;
            }
        }
        return applied;
    }
};

}  // namespace predix::wal
