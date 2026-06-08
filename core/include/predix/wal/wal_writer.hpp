#pragma once

#include "predix/engine/types.hpp"

#include <fstream>
#include <mutex>
#include <string>

namespace predix::wal {

class WalWriter {
public:
    explicit WalWriter(std::string path, bool flush_each_append = false)
        : path_(std::move(path)), flush_each_append_(flush_each_append) {
        out_.open(path_, std::ios::app);
    }

    void append(const std::string& record) {
        std::lock_guard lock(mutex_);
        if (!out_.is_open()) {
            out_.open(path_, std::ios::app);
        }
        out_ << record << '\n';
        if (flush_each_append_) {
            out_.flush();
        }
    }

    void sync() {
        std::lock_guard lock(mutex_);
        if (out_.is_open()) {
            out_.flush();
        }
    }

    void appendSubmit(const std::string& market_id, const std::string& outcome_id,
                      const engine::BookOrder& order) {
        append("SUBMIT|" + market_id + "|" + outcome_id + "|" + order.id + "|" + order.user_id +
               "|" + std::to_string(static_cast<int>(order.side)) + "|" +
               std::to_string(static_cast<int>(order.order_type)) + "|" + order.price.toString() +
               "|" + order.remaining_quantity.toString());
    }

    void appendCancel(const std::string& market_id, const std::string& outcome_id,
                      const std::string& order_id) {
        append("CANCEL|" + market_id + "|" + outcome_id + "|" + order_id);
    }

    const std::string& path() const { return path_; }

private:
    std::string path_;
    std::ofstream out_;
    std::mutex mutex_;
    bool flush_each_append_;
};

}  // namespace predix::wal
