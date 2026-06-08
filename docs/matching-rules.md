# Matching Rules (Phase-1)

Matching logic is implemented in **C++** (`core/include/predix/engine/order_book.hpp`). Java applies post-match lifecycle updates only — it does not perform matching in production.

## Binary market order book

Each `(market_id, outcome_id)` pair has an independent order book, sharded across C++ worker threads by hash of the pair.

## Price-time priority

### Limit orders

1. **Price priority**: Best price matches first.
   - Incoming **buy** matches against lowest ask on book.
   - Incoming **sell** matches against highest bid on book.
2. **Time priority**: At the same price, FIFO by arrival sequence (`created_at_ms`, then `sequence`).

### Market orders

- Immediately consume opposite-side liquidity at resting limit prices (price-time order).
- **No liquidity**: rejected with `ORDER_INSUFFICIENT_LIQUIDITY`.
- **Partial liquidity**: partial fill; unfilled remainder is **not** rested on book.

### Resting liquidity

Unfilled **limit** order quantity is added to the book after matching completes.

## Trade price

Always the **maker** (resting order) price.

## Order status transitions

| From | Allowed to |
|------|------------|
| NEW | PARTIAL, FILLED, CANCELLED, REJECTED, PENDING_MATCH |
| PARTIAL | FILLED, CANCELLED, PENDING_MATCH |
| PENDING_MATCH | PARTIAL, FILLED, CANCELLED |
| FILLED / CANCELLED / REJECTED | (terminal) |

Illegal transitions throw `ORDER_INVALID_TRANSITION`.

### `PENDING_MATCH`

Internal recovery state — not exposed to end users via a separate API. Set when:

1. gRPC `SubmitOrder` succeeds in C++
2. Java `finalizeMatch` (DB persist) fails

A background worker retries finalize using C++ idempotency (same `orderId` returns cached match result). Orders in `PENDING_MATCH` can still be cancelled.

## Cancel rules

| Status | Cancellable? |
|--------|--------------|
| NEW | Yes |
| PARTIAL | Yes |
| PENDING_MATCH | Yes |
| FILLED | No |
| CANCELLED | No |
| REJECTED | No |

Cancel removes the resting portion from the C++ book. Already-filled quantity is unchanged.

## Validation (Java, pre-match)

| Rule | Error code |
|------|------------|
| `quantity > 0` | `VALIDATION_ERROR` |
| Limit `price` in `[0.01, 0.99]` (configurable) | `ORDER_INVALID_PRICE` |
| Market must be `OPEN` for new orders | `ORDER_INVALID_MARKET_STATUS` |
| Valid `outcomeId` for market | `VALIDATION_ERROR` |

## Idempotency

| Layer | Key | Behavior |
|-------|-----|----------|
| HTTP / Java | `(userId, clientOrderId)` | Return cached `OrderResponse` |
| C++ gRPC | `orderId` (UUID) | Return cached `SubmitOrder` result or resting-order snapshot |
| Warmup | `orderId` | Skip duplicate `addToBook` on reload |

Safe to retry `POST /api/v1/orders` on network failure.

## Decimal precision

Prices and quantities use fixed-point scale **10⁸** over gRPC (`int64` raw). Java uses `BigDecimal` with up to 8 decimal places in persistence.

## Consistency with PostgreSQL

PostgreSQL is the source of truth for order/trade records. C++ in-memory books are rebuilt from DB via `WarmupBook` on:

- Service startup
- C++ health recovery
- Depth drift reconciliation
- Admin reload

Optional C++ WAL (`SUBMIT|…`, `CANCEL|…`) provides an append-only audit trail and optional startup replay — see [Architecture](architecture.md).

## Test coverage

C++ unit tests in `core/tests/` verify:

- Price priority (`LimitBuyMatchesLowestAskFirst_pricePriority`)
- FIFO (`SamePriceFifo_timePriority`)
- Market order rejection and partial fill
- Resting limit orders
- Submit/cancel idempotency
- WAL replay

Java tests verify status transitions (including `PENDING_MATCH`) and end-to-end flows via H2 stub or Testcontainers.

## Related docs

- [Architecture](architecture.md) — place/cancel transaction split, recovery
- [BFF integration](integration.md) — REST API and error codes
