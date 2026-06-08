ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('NEW', 'PARTIAL', 'PENDING_MATCH', 'FILLED', 'CANCELLED', 'REJECTED'));

CREATE INDEX idx_orders_pending_match ON orders (status, updated_at)
    WHERE status = 'PENDING_MATCH';
