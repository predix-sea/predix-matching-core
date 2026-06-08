# PrediX Matching Core

Production-grade off-chain matching for prediction markets. A **C++20 hot-path matching engine** paired with a **Java 21 orchestration layer**, designed as a drop-in replacement for the archived `predix-matching-engine` while preserving the existing BFF REST contract.

---

## Executive Summary

| Attribute | Detail |
|-----------|--------|
| **Hot path** | C++ in-memory order books, price–time FIFO matching, gRPC API |
| **Orchestration** | Java Spring Boot — REST, PostgreSQL, Redis, RabbitMQ, external integrations |
| **Source of truth** | PostgreSQL (orders, trades, lifecycle) |
| **Recovery** | DB-driven warmup; optional WAL append + startup replay; admin reload API |
| **Consistency** | Idempotent submit/cancel, split DB/gRPC transactions, fail-fast gRPC, depth reconciliation, `PENDING_MATCH` recovery |
| **Stack** | C++20 · Java 21 · Spring Boot 3.4 · PostgreSQL 16 · gRPC · Docker Compose · GitHub Actions CI |

Matching logic runs **only in C++**. Java does not maintain a production in-memory order book and does not perform fallback matching.

---

## Architecture

```
┌─────────────┐     REST      ┌──────────────────┐    gRPC     ┌─────────────────┐
│  BFF / API  │ ────────────► │  Java Service    │ ──────────► │  C++ Core       │
│  Gateway    │               │  (orchestration) │             │  (matching)     │
└─────────────┘               └────────┬─────────┘             └────────┬────────┘
                                       │                                  │
                                       ▼                                  ▼
                              ┌─────────────────┐             ┌─────────────────┐
                              │  PostgreSQL     │             │  WAL (optional) │
                              │  Redis · MQ     │             │  in-memory book │
                              └─────────────────┘             └─────────────────┘
```

### Component Responsibilities

| Module | Language | Responsibility |
|--------|----------|----------------|
| `core/` | C++20 | Order books, price–time priority matching, WAL append/replay, sharded execution, gRPC server (TLS optional) |
| `service/` | Java 21 | REST API, persistence, idempotency, MQ events, chain execution tasks, reconciliation, ops admin API |

### Consistency & Recovery Model

| Mechanism | Purpose |
|-----------|---------|
| **Split place/cancel flow** | Persist order in a committed DB transaction, call C++ outside the transaction, then finalize in a separate transaction — a successful match in C++ is not rolled back by a DB failure |
| **Warmup** | On startup, load `NEW` / `PARTIAL` / `PENDING_MATCH` orders from PostgreSQL into C++ via `WarmupBook` (replace mode) |
| **Idempotent submit** | Duplicate `SubmitOrder` for the same `orderId` returns the cached result — safe under gRPC retries |
| **Fail-fast gRPC** | On core unavailability, return `503 MATCHING_CORE_UNAVAILABLE` — no silent in-process fallback |
| **`PENDING_MATCH` worker** | If gRPC succeeds but DB finalize fails, mark the order `PENDING_MATCH` and retry on a schedule |
| **Health monitor** | After C++ recovery, trigger a full DB warmup automatically |
| **Depth reconciliation** | Periodically compare DB-aggregated depth vs C++ `GetDepth`; repair drift from PostgreSQL |
| **WAL replay (optional)** | C++ can replay `SUBMIT`/`CANCEL` records from disk on startup before accepting gRPC traffic |
| **Admin reload** | Operator-triggered full or per-book reload via authenticated REST endpoints |

---

## Verification & Test Results

All tests below were executed locally on **2026-06-08** (macOS, Java 21). Commands are included so results are reproducible.

### C++ Unit Tests (Google Test)

```bash
cd core
cmake -B build && cmake --build build
ctest --test-dir build --output-on-failure
```

**Result: 9 / 9 passed** (4 suites)

| Suite | Test | Coverage |
|-------|------|----------|
| `OrderBookTest` | `LimitBuyMatchesLowestAskFirst_pricePriority` | Price priority (best ask first) |
| | `SamePriceFifo_timePriority` | FIFO within same price level |
| | `MarketOrderWithNoLiquidity_rejected` | Market order rejection |
| | `MarketOrderPartialFill_whenLiquidityInsufficient` | Partial market fill |
| | `RestingLimitAddedWhenNoMatch` | Limit order rests on book |
| `OrderBookIdempotencyTest` | `DuplicateAddToBook_ignored` | Warmup / reload deduplication |
| | `SubmissionResultReplayed` | Cached submission replay |
| `OrderBookCancelTest` | `RemoveFromBook_removesRestingOrder` | Cancel removes resting order |
| `WalReplayTest` | `ReplaysSubmitAndCancelWithoutDoubleWrite` | WAL parse + replay without double-write |

### Java Unit Tests (JUnit 5 · Maven Surefire)

```bash
cd service
mvn test
```

**Result: 37 / 37 passed** · **BUILD SUCCESS** (~60 s)

| Test Class | Area |
|------------|------|
| `OrderServiceH2Test` | End-to-end place / match / cancel with H2 |
| `OrderServiceIdempotencyTest` | Client order id idempotency (integration profile) |
| `OrderStatusTransitionTest` | Order lifecycle state machine incl. `PENDING_MATCH` |
| `OrderBookReconciliationServiceTest` | DB vs C++ depth drift detection & repair |
| `OrderBookReconciliationMetricsTest` | Prometheus drift counters |
| `MatchingCoreHealthMonitorTest` | Recovery-triggered warmup |
| `PendingMatchRecoveryServiceTest` | Stuck-match retry worker |
| `OrderBookQueryH2Test` | Order book metadata + live depth |
| `MatchingEngineTest` | Post-match status & maker fill helpers |
| `OrderValidationServiceTest` | Request validation rules |
| `MarketLifecycleServiceTest` | Market status gates |
| Others | Cancel flow, DTO mapping, error envelope, code generation |

### Integration Tests (Testcontainers · optional)

```bash
cd service
mvn test -Pintegration
```

Runs `MatchingFlowIntegrationTest` and `OrderServiceIdempotencyTest` against PostgreSQL, Redis, and RabbitMQ via Testcontainers. Requires Docker. Also executed in CI (`java-integration` job).

### Combined Summary

| Layer | Tests | Failures | Status |
|-------|------:|---------:|--------|
| C++ (`matching_core_tests`) | 9 | 0 | PASS |
| Java (`mvn test`) | 37 | 0 | PASS |
| **Total (default suite)** | **46** | **0** | **PASS** |

### Continuous Integration

GitHub Actions (`.github/workflows/ci.yml`) runs on push/PR:

| Job | Command |
|-----|---------|
| `cpp` | `cmake -B build && cmake --build build && ctest --test-dir build` |
| `java` | `mvn test` |
| `java-integration` | `mvn test -Pintegration` (Docker required) |

---

## Quick Start (Docker Compose)

```bash
git clone <repo-url> && cd predix-matching-core
docker compose up --build
```

This starts PostgreSQL, Redis, RabbitMQ, the C++ matching core, and the Java service with gRPC enabled by default (`SPRING_PROFILES_ACTIVE=prod` in the service image).

| Endpoint | URL |
|----------|-----|
| REST API | http://localhost:8082 |
| Health | http://localhost:8082/actuator/health |
| Prometheus | http://localhost:8082/actuator/prometheus |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| RabbitMQ UI | http://localhost:15672 (predix / predix) |
| C++ gRPC | localhost:50051 |

The C++ container healthcheck calls the `Health` gRPC RPC via `grpcurl` (not just a TCP port check).

### BFF Integration

Point the gateway at the service — no BFF code changes required:

```bash
MATCHING_URL=http://localhost:8082
```

See [docs/integration.md](docs/integration.md) for details.

---

## Configuration

### Production / Docker (recommended)

```bash
MATCHING_CORE_GRPC_ENABLED=true          # default in prod profile / Docker image
MATCHING_CORE_HOST=matching-core         # use localhost when running bare-metal
MATCHING_CORE_PORT=50051
MATCHING_CORE_GRPC_DEADLINE_MS=5000
```

### gRPC TLS (optional)

**Java client** (`application.yml` or env):

```bash
MATCHING_CORE_GRPC_TLS_ENABLED=true
MATCHING_CORE_GRPC_TRUST_CERT=/path/to/ca.crt
```

**C++ server** (`core/config/core.yaml`):

```yaml
tls_cert_path: /app/certs/server.crt
tls_key_path: /app/certs/server.key
```

When TLS paths are unset, both sides use plaintext (local dev default).

### Reconciliation & health

```bash
MATCHING_CORE_HEALTH_CHECK_MS=30000           # C++ health probe interval
MATCHING_RECONCILIATION_ENABLED=true          # DB vs C++ depth reconciliation
MATCHING_RECONCILIATION_INTERVAL_MS=300000    # default: 5 minutes
MATCHING_RECONCILIATION_DEPTH_LEVELS=20
```

Prometheus metrics exposed when drift is detected:

- `predix.orderbook.drift.detected`
- `predix.orderbook.drift.repaired`

### Pending match recovery

```bash
PREDIX_PENDING_MATCH_ENABLED=true             # default: true
PREDIX_PENDING_MATCH_INTERVAL_MS=60000
PREDIX_PENDING_MATCH_BATCH_SIZE=50
```

### Admin reload API

```bash
PREDIX_ADMIN_ENABLED=true
PREDIX_ADMIN_API_KEY=<secret>                 # required when admin is enabled
```

Requests to `/api/v1/admin/**` must include header `X-Admin-Api-Key`.

### C++ core (`core/config/core.yaml`)

```yaml
shard_count: 4
listen_port: 50051
wal_path: /var/lib/predix/matching.wal
wal_flush_each_append: false   # true = durable per line, slower hot path
wal_replay_on_startup: false   # true = replay WAL before accepting gRPC
# tls_cert_path: /app/certs/server.crt
# tls_key_path: /app/certs/server.key
```

> If `grpc.enabled=false` outside of H2/test profiles, place/cancel/depth calls return `503`. Use Docker Compose or run the C++ binary alongside Java.

---

## API Examples

### Place order

```bash
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
  }'
```

Duplicate `clientOrderId` returns the same order (idempotency cache + C++ submission cache).

### Cancel order

```bash
curl -s -X POST http://localhost:8082/api/v1/orders/{orderId}/cancel
```

### Order book (metadata + depth)

```bash
curl -s http://localhost:8082/api/v1/orderbooks/mkt-demo/yes
```

Returns book status and the top 10 depth levels from the C++ core.

### Order book depth only

```bash
curl -s "http://localhost:8082/api/v1/orderbooks/mkt-demo/yes/depth?levels=10"
```

### Trade history

```bash
curl -s "http://localhost:8082/api/v1/trades?marketId=mkt-demo&page=0&size=20"
```

### Admin — reload all open books from DB

```bash
curl -s -X POST http://localhost:8082/api/v1/admin/orderbooks/reload \
  -H "X-Admin-Api-Key: ${PREDIX_ADMIN_API_KEY}"
```

### Admin — reload a single book (ResetBook + DB warmup)

```bash
curl -s -X POST http://localhost:8082/api/v1/admin/orderbooks/mkt-demo/yes/reload \
  -H "X-Admin-Api-Key: ${PREDIX_ADMIN_API_KEY}"
```

---

## Local Development

### Option A — Docker Compose (recommended)

```bash
docker compose up --build
```

### Option B — Bare metal (C++ + Java)

```bash
# Terminal 1 — C++ matching core
cd core && cmake -B build && cmake --build build && ./build/matching_core

# Terminal 2 — Java orchestration layer
cd service
MATCHING_CORE_GRPC_ENABLED=true MATCHING_CORE_HOST=localhost mvn spring-boot:run
```

### Run the full test suite

```bash
# C++
cd core && cmake -B build && cmake --build build && ctest --test-dir build --output-on-failure

# Java unit tests
cd service && mvn test

# Java integration tests (Docker required)
cd service && mvn test -Pintegration
```

H2 tests use an in-memory matching stub under `src/test/` and do not require a running C++ process.

---

## Project Structure

```
predix-matching-core/
├── core/              # C++ matching hot path (gRPC + WAL)
├── service/           # Java Spring Boot orchestration (no production matching)
├── docs/              # Architecture, matching rules, integration guides
├── .github/workflows/ # CI (C++, Java unit, Java integration)
└── docker-compose.yml # Full stack for local / demo deployment
```

---

## Documentation

- [Architecture](docs/architecture.md)
- [Matching rules](docs/matching-rules.md)
- [Java compatibility mapping](docs/java-compat-mapping.md)
- [BFF integration guide](docs/integration.md)

---

## Why Java for Orchestration

- Existing BFF client already targets the same REST contract and `ApiResponse` envelope — lowest migration cost
- Mature ecosystem for Flyway, JPA, RabbitMQ, Actuator, and Prometheus
- C++ owns latency-sensitive matching; Java owns persistence, messaging, and external service integration

---

## License & Status

Active development on branch `fix/matching-consistency`. Suitable for staging and production deployment with Docker Compose or Kubernetes (compose file provided as reference).
