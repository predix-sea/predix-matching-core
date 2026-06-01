#pragma once

#include <cstdint>
#include <cmath>
#include <limits>
#include <optional>
#include <stdexcept>
#include <string>

namespace predix::engine {

inline constexpr int64_t kDecimalScale = 100'000'000LL;

class Decimal {
public:
    Decimal() : raw_(0) {}

    explicit Decimal(int64_t raw) : raw_(raw) {}

    static Decimal fromRaw(int64_t raw) { return Decimal(raw); }

    static Decimal parse(const char* value) {
        if (value == nullptr) {
            return Decimal(0);
        }
        return fromString(value);
    }

    static Decimal fromString(const std::string& value) {
        if (value.empty()) {
            throw std::invalid_argument("empty decimal string");
        }

        bool negative = false;
        size_t index = 0;
        if (value[index] == '-') {
            negative = true;
            ++index;
        } else if (value[index] == '+') {
            ++index;
        }

        int64_t whole = 0;
        while (index < value.size() && value[index] >= '0' && value[index] <= '9') {
            whole = whole * 10 + (value[index] - '0');
            ++index;
        }

        int64_t fraction = 0;
        int64_t fraction_scale = 1;
        if (index < value.size() && value[index] == '.') {
            ++index;
            while (index < value.size() && value[index] >= '0' && value[index] <= '9') {
                if (fraction_scale < kDecimalScale) {
                    fraction = fraction * 10 + (value[index] - '0');
                    fraction_scale *= 10;
                }
                ++index;
            }
        }

        while (fraction_scale < kDecimalScale) {
            fraction *= 10;
            fraction_scale *= 10;
        }

        int64_t raw = whole * kDecimalScale + fraction;
        if (negative) {
            raw = -raw;
        }
        return Decimal(raw);
    }

    static std::optional<Decimal> tryFromString(const std::string& value) {
        try {
            return fromString(value);
        } catch (...) {
            return std::nullopt;
        }
    }

    int64_t raw() const { return raw_; }

    bool isZero() const { return raw_ == 0; }

    bool isPositive() const { return raw_ > 0; }

    bool isNegative() const { return raw_ < 0; }

    Decimal abs() const { return Decimal(raw_ < 0 ? -raw_ : raw_); }

    Decimal operator-() const { return Decimal(-raw_); }

    Decimal operator+(const Decimal& other) const { return Decimal(raw_ + other.raw_); }

    Decimal operator-(const Decimal& other) const { return Decimal(raw_ - other.raw_); }

    Decimal& operator+=(const Decimal& other) {
        raw_ += other.raw_;
        return *this;
    }

    Decimal& operator-=(const Decimal& other) {
        raw_ -= other.raw_;
        return *this;
    }

    bool operator==(const Decimal& other) const { return raw_ == other.raw_; }

    bool operator!=(const Decimal& other) const { return raw_ != other.raw_; }

    bool operator<(const Decimal& other) const { return raw_ < other.raw_; }

    bool operator<=(const Decimal& other) const { return raw_ <= other.raw_; }

    bool operator>(const Decimal& other) const { return raw_ > other.raw_; }

    bool operator>=(const Decimal& other) const { return raw_ >= other.raw_; }

    Decimal min(const Decimal& other) const {
        return raw_ <= other.raw_ ? *this : other;
    }

    std::string toString() const {
        const bool negative = raw_ < 0;
        int64_t abs_raw = negative ? -raw_ : raw_;
        int64_t whole = abs_raw / kDecimalScale;
        int64_t fraction = abs_raw % kDecimalScale;

        std::string result = negative ? "-" : "";
        result += std::to_string(whole);
        result += '.';

        std::string frac = std::to_string(fraction);
        while (frac.size() < 8) {
            frac = "0" + frac;
        }
        while (!frac.empty() && frac.back() == '0') {
            frac.pop_back();
        }
        if (frac.empty()) {
            frac = "0";
        }
        result += frac;
        return result;
    }

private:
    int64_t raw_;
};

inline Decimal minDecimal(const Decimal& a, const Decimal& b) {
    return a.min(b);
}

}  // namespace predix::engine
