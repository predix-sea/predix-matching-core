# Java Compatibility Mapping

> See also: [Architecture](architecture.md) · [BFF integration](integration.md) · [Matching rules](matching-rules.md)

Archived `predix-matching-engine` → new `predix-matching-core` module mapping.

| Archived (Java) | New location | Notes |
|-----------------|--------------|-------|
| `engine/InMemoryOrderBook` | `core/include/predix/engine/order_book.hpp` (prod) · `service/src/test/.../testsupport/inmemory/` (H2/test) | C++ hot path in production; Java in-memory stub only under `@Profile({"h2", "test"})` |
| `engine/MatchingEngine` | `core/` + `service/client/MatchingCoreClient` | C++ hot path via gRPC; Java helpers for post-match lifecycle only |
| `engine/OrderBookRegistry` | `core/include/predix/engine/book_registry.hpp` | Per-book registry in C++ |
| `service/OrderService` | `service/.../OrderService.java` | Split DB/gRPC flow via `OrderMatchPersistenceService` |
| `controller/*` | `service/.../controller/*` | Same REST paths and DTOs; added `AdminOrderBookController` (ops only) |
| `mq/*` | `service/.../mq/*` | Same exchange, queues, routing keys |
| `idempotency/*` | `service/.../idempotency/*` | Redis + DB; `saveResponse` upserts on retry |
| `db/migration/V1__init.sql` | `service/src/main/resources/db/migration/V1__init.sql` | Base schema |
| `db/migration/` (new) | `V2__pending_match_status.sql` | Adds `PENDING_MATCH` order status |
| `config/OrderBookWarmup` | `service/.../config/OrderBookWarmup.java` | Calls `WarmupBook` gRPC |
| `client/MarketSchemaClient` | `service/.../client/*` | Unchanged HTTP contract |
| `client/CtfExecutionGateway` | `service/.../client/*` | Unchanged HTTP contract |

## New components (not in archived engine)

| Component | Purpose |
|-----------|---------|
| `OrderMatchPersistenceService` | Isolated DB transactions for place/cancel finalize |
| `OrderBookReconciliationService` | DB vs C++ depth comparison + drift repair |
| `OrderBookReconciliationMetrics` | Prometheus drift counters |
| `MatchingCoreHealthMonitor` | C++ recovery detection + full warmup |
| `PendingMatchRecoveryService` | Background retry for `PENDING_MATCH` orders |
| `AdminOrderBookController` | Operator reload API (`ResetBook` + warmup) |
| `AdminAuthFilter` | `X-Admin-Api-Key` gate for admin routes |
| `GrpcMatchingCoreClient` | Production gRPC client (TLS optional) |
| `UnavailableMatchingCoreClient` | Fail-fast 503 when gRPC disabled (non-test profiles) |

## Intentionally not ported

| Archived | Reason |
|----------|--------|
| In-process `MatchingEngine` `@Component` | Replaced by `MatchingCoreClient` → C++ gRPC |
| Monolithic single-JVM book | Split: C++ match + Java orchestration |
| Dev-time Java fallback matching | Removed; `UnavailableMatchingCoreClient` returns 503 when gRPC disabled |
| Single `@Transactional` on `placeOrder` | Replaced by split persist → gRPC → finalize |

## gRPC proto

`core/proto/matching_core.proto` — internal contract between `service/` and `core/`.

| RPC | Java usage |
|-----|------------|
| `SubmitOrder` | `OrderService` place flow |
| `CancelOrder` | `OrderService` cancel flow |
| `GetDepth` | `OrderBookService.getDepth()` / `getOrderBook()` |
| `WarmupBook` | Startup warmup, health recovery, reconciliation, admin reload |
| `ResetBook` | Admin single-book reload |
| `Health` | `MatchingCoreHealthMonitor` |

## Order status mapping

| Status | Meaning |
|--------|---------|
| `NEW` | Persisted, awaiting or undergoing match |
| `PARTIAL` | Partially filled, remainder may rest on book |
| `PENDING_MATCH` | gRPC match succeeded; DB finalize pending retry |
| `FILLED` | Fully matched |
| `CANCELLED` | Cancelled by user |
| `REJECTED` | Rejected (e.g. market order with no liquidity) |

## Test profile mapping

| Profile | Matching client | Database |
|---------|-----------------|----------|
| `h2` | `InMemoryMatchingCoreClient` | H2 in-memory |
| `test` | `InMemoryMatchingCoreClient` | Testcontainers PostgreSQL |
| default / `prod` | `GrpcMatchingCoreClient` | PostgreSQL |

Integration tests are tagged `@Tag("integration")` and run via `mvn test -Pintegration`.

## Related docs

- [Architecture](architecture.md) — system design and recovery model
- [BFF integration](integration.md) — REST API contract
- [Matching rules](matching-rules.md) — order status and matching semantics
