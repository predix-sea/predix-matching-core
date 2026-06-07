# PrediX Matching Core

Production-grade off-chain matching for prediction markets. A **C++20 hot-path matching engine** paired with a **Java 21 orchestration layer**, designed as a drop-in replacement for the archived `predix-matching-engine` while preserving the existing BFF REST contract.

---

## Executive Summary

| Attribute | Detail |
|-----------|--------|
| **Hot path** | C++ in-memory order books, price–time FIFO matching, gRPC API |
| **Orchestration** | Java Spring Boot — REST, PostgreSQL, Redis, RabbitMQ, external integrations |
| **Source of truth** | PostgreSQL (orders, trades, lifecycle) |
| **Recovery** | DB-driven warmup into C++ on startup; optional WAL audit trail |
| **Consistency** | Idempotent submit/cancel, fail-fast gRPC (no silent Java fallback), scheduled depth reconciliation |
| **Stack** | C++20 · Java 21 · Spring Boot 3.4 · PostgreSQL 16 · gRPC · Docker Compose |

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
| `core/` | C++20 | Order books, price–time priority matching, WAL append log, sharded execution, gRPC server |
| `service/` | Java 21 | REST API, persistence, idempotency, MQ events, chain execution tasks, market-schema / CTF clients |

### Consistency & Recovery Model

| Mechanism | Purpose |
|-----------|---------|
| **Warmup** | On startup, load `NEW` / `PARTIAL` orders from PostgreSQL into C++ via `WarmupBook` (replace mode: clear book, then reload) |
| **Idempotent submit** | Duplicate `SubmitOrder` for the same `orderId` returns the cached result — safe under gRPC retries |
| **Fail-fast gRPC** | On core unavailability, return `503 MATCHING_CORE_UNAVAILABLE` — no silent in-process fallback |
| **Health monitor** | After C++ recovery, trigger a full DB warmup automatically |
| **Depth reconciliation** | Periodically compare DB-aggregated depth vs C++ `GetDepth`; repair drift from PostgreSQL |

---

## Verification & Test Results

All tests below were executed locally on **2026-06-08** (macOS, Java 21.0.11). Commands are included so results are reproducible.

### C++ Unit Tests (Google Test)

```bash
cd core
cmake -B build && cmake --build build
./build/matching_core_tests
```

**Result: 8 / 8 passed** (3 suites, ~0 ms)

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

### Java Unit & Integration Tests (JUnit 5 · Maven Surefire)

```bash
cd service
mvn test
```

**Result: 23 / 23 passed** · **BUILD SUCCESS** (~35 s)

| Test Class | Tests | Area |
|------------|------:|------|
| `OrderStatusTransitionTest` | 9 | Order lifecycle state machine |
| `OrderServiceH2Test` | 3 | End-to-end place / match / cancel with H2 |
| `OrderValidationServiceTest` | 2 | Request validation rules |
| `MatchingEngineTest` | 2 | Post-match status & maker fill helpers |
| `OrderBookQueryH2Test` | 1 | Order book & depth query |
| `MarketLifecycleServiceTest` | 1 | Market status gates |
| `OrderCancelUnitTest` | 1 | Cancel flow |
| `CodeGeneratorTest` | 1 | Order / trade code generation |
| `DtoMapperTest` | 1 | API DTO mapping |
| `GlobalExceptionHandlerTest` | 1 | Error response contract |
| `ApiResponseTest` | 1 | Unified API envelope |

> **Note:** Docker-based integration tests (`MatchingFlowIntegrationTest`, tagged `integration`) require Testcontainers and are excluded from the default `mvn test` run.

### Combined Summary

| Layer | Tests | Failures | Status |
|-------|------:|---------:|--------|
| C++ (`matching_core_tests`) | 8 | 0 | PASS |
| Java (`mvn test`) | 23 | 0 | PASS |
| **Total** | **31** | **0** | **PASS** |

---

## Quick Start (Docker Compose)

```bash
git clone <repo-url> && cd predix-matching-core
docker compose up --build
```

This starts PostgreSQL, Redis, RabbitMQ, the C++ matching core, and the Java service with gRPC enabled by default.

| Endpoint | URL |
|----------|-----|
| REST API | http://localhost:8082 |
| Health | http://localhost:8082/actuator/health |
| Prometheus | http://localhost:8082/actuator/prometheus |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| RabbitMQ UI | http://localhost:15672 (predix / predix) |
| C++ gRPC | localhost:50051 |

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
MATCHING_CORE_GRPC_ENABLED=true
MATCHING_CORE_HOST=matching-core      # use localhost when running bare-metal
MATCHING_CORE_PORT=50051
MATCHING_CORE_GRPC_DEADLINE_MS=5000
```

### Optional tuning

```bash
MATCHING_CORE_HEALTH_CHECK_MS=30000           # C++ health probe interval
MATCHING_RECONCILIATION_ENABLED=true          # DB vs C++ depth reconciliation
MATCHING_RECONCILIATION_INTERVAL_MS=300000    # default: 5 minutes
MATCHING_RECONCILIATION_DEPTH_LEVELS=20
```

### C++ WAL (`core/config/core.yaml`)

```yaml
wal_path: /var/lib/predix/matching.wal
wal_flush_each_append: false   # true = durable per line, slower hot path
```

> If `grpc.enabled=false` outside of H2 tests, place/cancel/depth calls return `503`. Use Docker Compose or run the C++ binary alongside Java.

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

### Cancel order

```bash
curl -s -X POST http://localhost:8082/api/v1/orders/{orderId}/cancel
```

### Order book depth

```bash
curl -s "http://localhost:8082/api/v1/orderbooks/mkt-demo/yes/depth?levels=10"
```

### Trade history

```bash
curl -s "http://localhost:8082/api/v1/trades?marketId=mkt-demo&page=0&size=20"
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
cd core && cmake -B build && cmake --build build && ./build/matching_core_tests

# Java
cd service && mvn test
```

H2 integration tests use an in-memory stub client under `src/test/` and do not require a running C++ process.

---

## Project Structure

```
predix-matching-core/
├── core/              # C++ matching hot path (gRPC + WAL)
├── service/           # Java Spring Boot orchestration (no production matching)
├── docs/              # Architecture, matching rules, integration guides
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

Active development. Suitable for staging and production deployment with Docker Compose or Kubernetes (compose file provided as reference).
