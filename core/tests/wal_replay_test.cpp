#include "predix/engine/matching_core.hpp"
#include "predix/wal/wal_reader.hpp"
#include "predix/wal/wal_writer.hpp"

#include <gtest/gtest.h>

#include <cstdio>
#include <filesystem>
#include <string>

using namespace predix::engine;

static BookOrder limitOrder(const std::string& id, Side side, const char* price, const char* qty) {
    return BookOrder{
        id,
        "user-" + id,
        side,
        OrderType::LIMIT,
        Decimal::parse(price),
        Decimal::parse(qty),
        1000,
        1,
    };
}

TEST(WalReplayTest, ReplaysSubmitAndCancelWithoutDoubleWrite) {
    const std::string path = (std::filesystem::temp_directory_path() / "predix-wal-replay-test.wal").string();
    std::filesystem::remove(path);

    auto wal = std::make_shared<predix::wal::WalWriter>(path, true);
    MatchingCore core(1, wal);

    core.submitOrder("mkt-wal", "yes", limitOrder("rest-1", Side::SELL, "0.55", "10"));
    core.cancelOrder("mkt-wal", "yes", "rest-1");

    MatchingCore replayed(1, wal);
    const int applied = predix::wal::WalReader::replay(path, replayed);
    ASSERT_EQ(applied, 2);

    const auto depth = replayed.getDepth("mkt-wal", "yes", 5);
    EXPECT_TRUE(depth.empty());

    std::filesystem::remove(path);
}
