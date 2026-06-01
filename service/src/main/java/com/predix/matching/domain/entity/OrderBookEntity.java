package com.predix.matching.domain.entity;

import com.predix.matching.domain.enums.OrderBookStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_id", nullable = false, length = 64)
    private String marketId;

    @Column(name = "outcome_id", nullable = false, length = 64)
    private String outcomeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderBookStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }
}
