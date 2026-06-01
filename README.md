# predix-matching-core

PrediX 链下撮合与编排服务 — C++ 热路径撮合内核 + Java 编排层，完整替代已归档的 `predix-matching-engine`。

## 架构选型

| 模块 | 语言 | 职责 |
|------|------|------|
| `core/` | C++20 | 内存订单簿、价格-时间优先撮合、WAL、shard 单线程、gRPC（演进中） |
| `service/` | Java 21 Spring Boot | REST API、PostgreSQL、Redis、RabbitMQ、market-schema/CTF 调用 |

**编排层为何选 Java 21 Spring Boot（而非 Go）**

- BFF `MatchingEngineClient` 已对接相同 REST 契约与 `ApiResponse` 格式，Java 实现迁移成本最低
- 团队与归档栈一致，Flyway/JPA/RabbitMQ/Actuator 生态成熟
- 编排层为全新代码，仅复制兼容契约；C++ 承担性能热路径

## 快速启动

```bash
cd predix-matching-core
docker compose up --build
```

- **API**: http://localhost:8082
- **Health**: http://localhost:8082/actuator/health
- **Prometheus**: http://localhost:8082/actuator/prometheus
- **Swagger**: http://localhost:8082/swagger-ui.html
- **RabbitMQ UI**: http://localhost:15672 (predix/predix)
- **C++ core**: port 50051 (WAL + 进程；gRPC 完整接入见 `predix.matching-core.grpc.enabled=true`)

## BFF 切换

仅修改环境变量，无需改 BFF 代码：

```bash
MATCHING_URL=http://localhost:8082
```

详见 [docs/integration.md](docs/integration.md)

## API 示例

### 下单

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

### 撤单

```bash
curl -s -X POST http://localhost:8082/api/v1/orders/{orderId}/cancel
```

### 订单簿深度

```bash
curl -s "http://localhost:8082/api/v1/orderbooks/mkt-demo/yes/depth?levels=10"
```

### 成交列表

```bash
curl -s "http://localhost:8082/api/v1/trades?marketId=mkt-demo&page=0&size=20"
```

## 本地开发

### C++ 单测

```bash
cd core
cmake -B build && cmake --build build
./build/matching_core_tests
```

### Java 服务

```bash
cd service
mvn spring-boot:run
```

## 文档

- [架构说明](docs/architecture.md)
- [撮合规则](docs/matching-rules.md)
- [Java 兼容对照](docs/java-compat-mapping.md)
- [BFF 集成指南](docs/integration.md)

## 项目结构

```
predix-matching-core/
├── core/           # C++ 撮合热路径
├── service/        # Java Spring Boot 编排层
├── docs/
└── docker-compose.yml
```
