package com.predix.matching.engine;

import com.predix.matching.engine.model.BookOrder;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

class PriceLevel {

    private final BigDecimal price;
    private final Deque<BookOrder> orders = new ArrayDeque<>();

    PriceLevel(BigDecimal price) {
        this.price = price;
    }

    BigDecimal getPrice() {
        return price;
    }

    void add(BookOrder order) {
        orders.addLast(order);
    }

    BookOrder peek() {
        return orders.peekFirst();
    }

    BookOrder poll() {
        return orders.pollFirst();
    }

    void requeueFront(BookOrder order) {
        orders.addFirst(order);
    }

    boolean isEmpty() {
        return orders.isEmpty();
    }

    int size() {
        return orders.size();
    }

    java.math.BigDecimal totalQuantity() {
        return orders.stream()
                .map(BookOrder::getRemainingQuantity)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    java.util.Iterator<BookOrder> iterator() {
        return orders.iterator();
    }
}
