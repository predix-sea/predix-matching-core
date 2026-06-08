ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('NEW', 'PARTIAL', 'PENDING_MATCH', 'PENDING_CANCEL', 'FILLED', 'CANCELLED', 'REJECTED'));

CREATE INDEX idx_orders_pending_cancel ON orders (status, updated_at)
    WHERE status = 'PENDING_CANCEL';
