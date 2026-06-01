# Java Compatibility Mapping

Archived `predix-matching-engine` → new `predix-matching-core` module mapping.

| Archived (Java) | New location | Notes |
|-----------------|--------------|-------|
| `engine/InMemoryOrderBook` | `core/include/predix/engine/order_book.hpp` | C++ hot path; behavior verified by ported tests |
| `engine/MatchingEngine` | `core/` + `service/client/MatchingCoreClient` | Orchestration calls gRPC; local adapter for dev |
| `engine/OrderBookRegistry` | `core/include/predix/engine/book_registry.hpp` | Per-book registry in C++ |
| `service/OrderService` | `service/.../OrderService.java` | Uses `MatchingCoreClient` instead of in-process engine |
| `controller/*` | `service/.../controller/*` | Same REST paths and DTOs |
| `mq/*` | `service/.../mq/*` | Same exchange, queues, routing keys |
| `idempotency/*` | `service/.../idempotency/*` | Redis + DB |
| `db/migration/V1__init.sql` | `service/src/main/resources/db/migration/V1__init.sql` | Identical schema |
| `config/OrderBookWarmup` | `service/.../config/OrderBookWarmup.java` | Calls `WarmupBook` gRPC |
| `client/MarketSchemaClient` | `service/.../client/*` | Unchanged HTTP contract |
| `client/CtfExecutionGateway` | `service/.../client/*` | Unchanged HTTP contract |

## Intentionally not ported

| Archived | Reason |
|----------|--------|
| In-process `MatchingEngine` `@Component` | Replaced by `MatchingCoreClient` |
| Monolithic single-JVM book | Split: C++ match + Java orchestration |

## gRPC proto

`core/proto/matching_core.proto` — internal contract between `service/` and `core/`.
