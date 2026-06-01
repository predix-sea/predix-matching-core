# BFF Integration Guide

## Switch from archived engine

The BFF `MatchingEngineClient` calls:

- `POST /api/v1/orders`
- `POST /api/v1/orders/{id}/cancel`

No BFF code changes required. Update the matching service URL only.

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MATCHING_URL` | — | BFF: set to `http://localhost:8082` (or K8s service DNS) |
| `SERVER_PORT` | `8082` | Matching service HTTP port |
| `DB_HOST` | `localhost` | PostgreSQL |
| `REDIS_HOST` | `localhost` | Idempotency cache |
| `RABBITMQ_HOST` | `localhost` | Event/execution queues |
| `MARKET_SCHEMA_URL` | `http://localhost:8081` | Market OPEN validation |
| `CTF_GATEWAY_URL` | `http://localhost:8083` | On-chain submit/cancel |
| `MATCHING_CORE_GRPC_ENABLED` | `false` | Enable gRPC to C++ core |
| `MATCHING_CORE_HOST` | `localhost` | C++ core host |
| `MATCHING_CORE_PORT` | `50051` | C++ core gRPC port |

## Docker Compose

```bash
cd predix-matching-core
docker compose up --build
```

BFF:

```bash
export MATCHING_URL=http://localhost:8082
```

## Smoke test

```bash
curl -s http://localhost:8082/actuator/health
curl -s -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-BFF-User-Id: user-001" \
  -d '{"marketId":"mkt-demo","outcomeId":"yes","userId":"user-001","side":"BUY","orderType":"LIMIT","price":"0.55","quantity":"10","clientOrderId":"cli-001"}'
```

Note: market-schema must report market OPEN unless using a mock/disabled validation profile.
