# Matching Rules (Phase-1)

## Binary market order book

Each `(market_id, outcome_id)` pair has an independent order book.

## Price-time priority

### Limit orders

1. **Price priority**: Best price matches first.
   - Buys: highest bid vs lowest ask on book.
   - Sells: lowest ask vs highest bid on book.
2. **Time priority**: At the same price, FIFO by arrival sequence.

### Market orders

- Immediately consume opposite side liquidity at resting limit prices.
- **No liquidity**: rejected (`ORDER_INSUFFICIENT_LIQUIDITY`).
- **Partial liquidity**: partial fill; remainder not rested on book.

### Resting liquidity

Unfilled **limit** order quantity is added to the book after matching completes.

## Order status transitions

| From | Allowed to |
|------|------------|
| NEW | PARTIAL, FILLED, CANCELLED, REJECTED |
| PARTIAL | FILLED, CANCELLED |
| FILLED / CANCELLED / REJECTED | (terminal) |

Illegal transitions throw `ORDER_INVALID_TRANSITION`.

## Validation

- `quantity > 0`
- Limit `price` in `[0.01, 0.99]` (configurable)
- Market must be `OPEN` for new orders

## Trade price

Always the **maker** (resting order) price.
