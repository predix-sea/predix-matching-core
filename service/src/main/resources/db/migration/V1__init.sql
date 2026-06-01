-- PrediX Matching Engine - Initial Schema (UTC timestamps)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE order_books (
    id              BIGSERIAL PRIMARY KEY,
    market_id       VARCHAR(64)  NOT NULL,
    outcome_id      VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_order_books_market_outcome UNIQUE (market_id, outcome_id)
);

CREATE INDEX idx_order_books_market ON order_books (market_id);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code          VARCHAR(32)  NOT NULL UNIQUE,
    market_id           VARCHAR(64)  NOT NULL,
    outcome_id          VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(64)  NOT NULL,
    side                VARCHAR(8)   NOT NULL,
    order_type          VARCHAR(16)  NOT NULL,
    price               NUMERIC(20, 8),
    quantity            NUMERIC(20, 8) NOT NULL,
    remaining_quantity  NUMERIC(20, 8) NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    client_order_id     VARCHAR(128) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_orders_client_user UNIQUE (user_id, client_order_id),
    CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_orders_type CHECK (order_type IN ('LIMIT', 'MARKET')),
    CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'PARTIAL', 'FILLED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX idx_orders_market_outcome ON orders (market_id, outcome_id);
CREATE INDEX idx_orders_user ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_market_status ON orders (market_id, status);

CREATE TABLE trades (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_code      VARCHAR(32)  NOT NULL UNIQUE,
    market_id       VARCHAR(64)  NOT NULL,
    outcome_id      VARCHAR(64)  NOT NULL,
    buy_order_id    UUID         NOT NULL REFERENCES orders (id),
    sell_order_id   UUID         NOT NULL REFERENCES orders (id),
    price           NUMERIC(20, 8) NOT NULL,
    quantity        NUMERIC(20, 8) NOT NULL,
    notional        NUMERIC(20, 8) NOT NULL,
    maker_user_id   VARCHAR(64)  NOT NULL,
    taker_user_id   VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC')
);

CREATE INDEX idx_trades_market_outcome ON trades (market_id, outcome_id);
CREATE INDEX idx_trades_created ON trades (created_at DESC);

CREATE TABLE execution_tasks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_code           VARCHAR(32)  NOT NULL UNIQUE,
    market_id           VARCHAR(64)  NOT NULL,
    task_type           VARCHAR(32)  NOT NULL,
    payload             JSONB        NOT NULL DEFAULT '{}',
    status              VARCHAR(16)  NOT NULL,
    retry_count         INT          NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMPTZ,
    idempotency_key     VARCHAR(256) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_execution_tasks_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_execution_task_type CHECK (task_type IN ('CTF_TRADE_SUBMIT', 'CTF_CANCEL', 'CTF_RETRY')),
    CONSTRAINT chk_execution_task_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYING', 'DEAD'))
);

CREATE INDEX idx_execution_tasks_status ON execution_tasks (status);
CREATE INDEX idx_execution_tasks_next_retry ON execution_tasks (next_retry_at) WHERE status IN ('RETRYING', 'FAILED');

CREATE TABLE engine_events (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    ref_id      VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC')
);

CREATE INDEX idx_engine_events_type ON engine_events (event_type);
CREATE INDEX idx_engine_events_ref ON engine_events (ref_id);
CREATE INDEX idx_engine_events_created ON engine_events (created_at DESC);

CREATE TABLE idempotency_records (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    resource_type   VARCHAR(64)  NOT NULL,
    resource_id     VARCHAR(128) NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_records (expires_at);
