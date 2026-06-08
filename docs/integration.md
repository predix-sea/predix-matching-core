# BFF Integration Guide

## Switch from archived engine

The BFF `MatchingEngineClient` calls the same REST endpoints as the archived `predix-matching-engine`:

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/orders` | Place order |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancel order |
| `GET` | `/api/v1/orders/{id}` | Get order by ID |
| `GET` | `/api/v1/orders` | List orders (filtered, paginated) |
| `GET` | `/api/v1/orderbooks/{marketId}/{outcomeId}` | Order book metadata + top 10 depth |
| `GET` | `/api/v1/orderbooks/{marketId}/{outcomeId}/depth` | Depth only (`?levels=10`) |
| `GET` | `/api/v1/trades` | Trade history (filtered, paginated) |

**No BFF code changes required** for the above. Update the matching service URL only.

Admin endpoints under `/api/v1/admin/**` are for ops tooling — not part of the BFF contract.

## Response envelope

All endpoints return the unified `ApiResponse` shape:

```json
{
  "code": "OK",
  "message": "Success",
  "data": { ... },
  "traceId": "...",
  "timestamp": "2026-06-08T00:00:00Z"
}
```

Errors use the same envelope with `code` set to an `ErrorCode` value (e.g. `MATCHING_CORE_UNAVAILABLE`).

## Headers

| Header | Required | Purpose |
|--------|----------|---------|
| `Content-Type: application/json` | POST bodies | Request body format |
| `X-BFF-User-Id` | Recommended | Passed through for audit; trusted when `predix.security.trust-bff-user-header=true` |

## Idempotency

Duplicate `POST /api/v1/orders` with the same `(userId, clientOrderId)` returns the **same order response** without creating a second order. Safe to retry on network timeout.

Implementation layers:

1. Java `IdempotencyService` — cached HTTP response
2. C++ submission cache — idempotent `SubmitOrder` on same `orderId`

## Environment variables

### BFF (client)

| Variable | Example | Description |
|----------|---------|-------------|
| `MATCHING_URL` | `http://localhost:8082` | Base URL for all matching REST calls |

### Matching service — core

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8082` | HTTP port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `predix_matching` | Database name |
| `DB_USER` / `DB_PASSWORD` | `predix` | Credentials |
| `REDIS_HOST` | `localhost` | Idempotency cache |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `localhost` | Event / execution queues |
| `RABBITMQ_PORT` | `5672` | AMQP port |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `predix` | RabbitMQ credentials |

### External services

| Variable | Default | Description |
|----------|---------|-------------|
| `MARKET_SCHEMA_URL` | `http://localhost:8081` | Market OPEN validation |
| `CTF_GATEWAY_URL` | `http://localhost:8083` | On-chain submit/cancel |
| `INDEXER_URL` | `http://localhost:8084` | Optional indexer |
| `INDEXER_ENABLED` | `false` | Enable indexer client |

### C++ matching core (gRPC)

| Variable | Default | Description |
|----------|---------|-------------|
| `MATCHING_CORE_GRPC_ENABLED` | `false` (local), `true` (Docker/prod) | Enable gRPC client |
| `MATCHING_CORE_HOST` | `localhost` | C++ core host |
| `MATCHING_CORE_PORT` | `50051` | C++ core gRPC port |
| `MATCHING_CORE_GRPC_DEADLINE_MS` | `5000` | Per-RPC deadline |
| `MATCHING_CORE_GRPC_TLS_ENABLED` | `false` | Enable TLS on Java gRPC client |
| `MATCHING_CORE_GRPC_TRUST_CERT` | — | CA cert path (required when TLS enabled) |

When `MATCHING_CORE_GRPC_ENABLED=false` outside test profiles, place/cancel/depth return **`503 MATCHING_CORE_UNAVAILABLE`**.

### Recovery & ops (optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `MATCHING_CORE_HEALTH_CHECK_MS` | `30000` | C++ health probe interval |
| `MATCHING_RECONCILIATION_ENABLED` | `true` | DB vs C++ depth reconciliation |
| `MATCHING_RECONCILIATION_INTERVAL_MS` | `300000` | Reconciliation interval (5 min) |
| `MATCHING_RECONCILIATION_DEPTH_LEVELS` | `20` | Depth levels compared |
| `PREDIX_PENDING_MATCH_ENABLED` | `true` | Background retry for stuck matches |
| `PREDIX_PENDING_MATCH_INTERVAL_MS` | `60000` | Pending-match worker interval |
| `PREDIX_PENDING_CANCEL_ENABLED` | `true` | Background retry for stuck cancels |
| `PREDIX_PENDING_CANCEL_INTERVAL_MS` | `60000` | Pending-cancel worker interval |
| `PREDIX_ADMIN_ENABLED` | `false` | Enable admin reload API |
| `PREDIX_ADMIN_API_KEY` | — | Required when admin enabled |

## Docker Compose

```bash
cd predix-matching-core
docker compose up --build
```

Stack includes PostgreSQL, Redis, RabbitMQ, C++ matching core, and Java service. gRPC is enabled by default; the Java image uses `SPRING_PROFILES_ACTIVE=prod`.

BFF:

```bash
export MATCHING_URL=http://localhost:8082
```

| Service | URL |
|---------|-----|
| REST API | http://localhost:8082 |
| Actuator health | http://localhost:8082/actuator/health |
| Prometheus | http://localhost:8082/actuator/prometheus |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| C++ gRPC | localhost:50051 |

## Smoke test

```bash
# Health
curl -s http://localhost:8082/actuator/health | jq .

# Place limit order (market must be OPEN in market-schema, or use mock profile)
curl -s -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-BFF-User-Id: user-001" \
  -d '{
    "marketId": "mkt-demo",
    "outcomeId": "yes",
    "userId": "user-001",
    "side": "BUY",
    "orderType": "LIMIT",
    "price": "0.55",
    "quantity": "10",
    "clientOrderId": "cli-001"
  }' | jq .

# Order book with depth
curl -s http://localhost:8082/api/v1/orderbooks/mkt-demo/yes | jq .

# Depth only
curl -s "http://localhost:8082/api/v1/orderbooks/mkt-demo/yes/depth?levels=5" | jq .
```

### Idempotency smoke test

Repeat the place-order curl with the same `clientOrderId` — response should contain the same `id`.

## Common error codes

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Invalid request fields |
| `ORDER_INVALID_MARKET_STATUS` | 400 | Market not OPEN |
| `ORDER_INVALID_PRICE` | 400 | Price outside `[0.01, 0.99]` |
| `ORDER_INSUFFICIENT_LIQUIDITY` | 400 | Market order with no liquidity |
| `ORDER_ALREADY_FINALIZED` | 409 | Cancel on terminal order |
| `ORDER_INVALID_TRANSITION` | 409 | Illegal status change |
| `NOT_FOUND` | 404 | Order / resource not found |
| `MATCHING_CORE_UNAVAILABLE` | 503 | C++ core unreachable or gRPC disabled |
| `MATCHING_CORE_UNCERTAIN` | 503 | gRPC outcome unknown (e.g. timeout); order marked for recovery |
| `MARKET_SCHEMA_UNAVAILABLE` | 503 | Market-schema service down |

## Observability

Prometheus metrics at `/actuator/prometheus`. Custom counters:

- `predix.orderbook.drift.detected` — depth drift detected between DB and C++
- `predix.orderbook.drift.repaired` — drift repaired via DB warmup

## Related docs

- [Architecture](architecture.md) — place/cancel flow, recovery model
- [Matching rules](matching-rules.md) — price-time priority, status transitions
- [Java compat mapping](java-compat-mapping.md) — migration from archived engine
