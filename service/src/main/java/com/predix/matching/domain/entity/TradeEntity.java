package com.predix.matching.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trade_code", nullable = false, unique = true, length = 32)
    private String tradeCode;

    @Column(name = "market_id", nullable = false, length = 64)
    private String marketId;

    @Column(name = "outcome_id", nullable = false, length = 64)
    private String outcomeId;

    @Column(name = "buy_order_id", nullable = false)
    private UUID buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private UUID sellOrderId;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal notional;

    @Column(name = "maker_user_id", nullable = false, length = 64)
    private String makerUserId;

    @Column(name = "taker_user_id", nullable = false, length = 64)
    private String takerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
