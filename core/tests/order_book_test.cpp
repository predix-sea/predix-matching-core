#include "predix/engine/order_book.hpp"

#include <gtest/gtest.h>

#include <string>

using namespace predix::engine;

static BookOrder limitOrder(const std::string& id, Side side, const char* price, const char* qty,
                            int64_t seq) {
    return BookOrder{
        id,
        "user-" + std::to_string(seq),
        side,
        OrderType::LIMIT,
        price == nullptr ? Decimal(0) : Decimal::parse(price),
        Decimal::parse(qty),
        seq * 1000,
        seq,
    };
}

class OrderBookTest : public ::testing::Test {
protected:
    OrderBook book{"mkt-1", "yes"};
};

TEST_F(OrderBookTest, LimitBuyMatchesLowestAskFirst_pricePriority) {
    book.addToBook(limitOrder("s1", Side::SELL, "0.60", "10", 1));
    book.addToBook(limitOrder("s2", Side::SELL, "0.55", "10", 2));
    book.addToBook(limitOrder("s3", Side::SELL, "0.50", "10", 3));

    const auto result = book.match(limitOrder("b1", Side::BUY, "0.60", "15", 4));

    ASSERT_EQ(result.fills.size(), 2u);
    EXPECT_EQ(result.fills[0].price, Decimal::parse("0.50"));
    EXPECT_EQ(result.fills[1].price, Decimal::parse("0.55"));
}

TEST_F(OrderBookTest, SamePriceFifo_timePriority) {
    const std::string first = "11111111-1111-1111-1111-111111111111";
    const std::string second = "22222222-2222-2222-2222-222222222222";
    book.addToBook(limitOrder(first, Side::SELL, "0.50", "5", 1));
    book.addToBook(limitOrder(second, Side::SELL, "0.50", "5", 2));

    const auto result = book.match(limitOrder("b1", Side::BUY, "0.50", "5", 3));

    ASSERT_EQ(result.fills.size(), 1u);
    EXPECT_EQ(result.fills[0].maker_order_id, first);
}

TEST_F(OrderBookTest, MarketOrderWithNoLiquidity_rejected) {
    BookOrder market_buy{"m1", "user-1", Side::BUY, OrderType::MARKET,
                         Decimal(0), Decimal::parse("5"), 1000, 1};
    const auto result = book.match(market_buy);
    EXPECT_TRUE(result.rejected);
    EXPECT_TRUE(result.fills.empty());
}

TEST_F(OrderBookTest, MarketOrderPartialFill_whenLiquidityInsufficient) {
    book.addToBook(limitOrder("s1", Side::SELL, "0.50", "3", 1));
    BookOrder market_buy{"m1", "user-2", Side::BUY, OrderType::MARKET,
                         Decimal(0), Decimal::parse("10"), 2000, 2};
    const auto result = book.match(market_buy);

    ASSERT_EQ(result.fills.size(), 1u);
    EXPECT_EQ(result.fills[0].quantity, Decimal::parse("3"));
    EXPECT_EQ(result.incoming_order.remaining_quantity, Decimal::parse("7"));
    EXPECT_FALSE(result.fully_filled);
    EXPECT_FALSE(result.rejected);
}

TEST_F(OrderBookTest, RestingLimitAddedWhenNoMatch) {
    const auto result = book.match(limitOrder("b1", Side::BUY, "0.40", "10", 1));
    EXPECT_TRUE(result.fills.empty());
    EXPECT_EQ(result.incoming_order.remaining_quantity, Decimal::parse("10"));
    EXPECT_FALSE(book.getDepth(5).empty());
}

TEST(OrderBookCancelTest, RemoveFromBook_removesRestingOrder) {
    OrderBook book{"m", "yes"};
    const std::string id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    book.addToBook(limitOrder(id, Side::BUY, "0.40", "10", 1));
    EXPECT_TRUE(book.removeFromBook(id));
    int buy_levels = 0;
    for (const auto& d : book.getDepth(5)) {
        if (d.side == Side::BUY) {
            ++buy_levels;
        }
    }
    EXPECT_EQ(buy_levels, 0);
}
