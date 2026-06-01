#pragma once

#include "predix/engine/types.hpp"
#include <deque>

namespace predix::engine {

class PriceLevel {
public:
    explicit PriceLevel(Decimal price) : price_(price) {}

    Decimal price() const { return price_; }

    void add(BookOrder order) { orders_.push_back(std::move(order)); }

    const BookOrder* peek() const {
        return orders_.empty() ? nullptr : &orders_.front();
    }

    BookOrder poll() {
        BookOrder o = std::move(orders_.front());
        orders_.pop_front();
        return o;
    }

    void requeueFront(BookOrder order) { orders_.push_front(std::move(order)); }

    bool empty() const { return orders_.empty(); }

    Decimal totalQuantity() const {
        Decimal sum(0);
        for (const auto& o : orders_) {
            sum = sum + o.remaining_quantity;
        }
        return sum;
    }

private:
    Decimal price_;
    std::deque<BookOrder> orders_;
};

}  // namespace predix::engine
