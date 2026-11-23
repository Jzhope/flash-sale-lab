CREATE TABLE app_users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE auth_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_token_expiry ON auth_tokens(expires_at);
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    price_cents BIGINT NOT NULL,
    stock INT NOT NULL,
    CHECK (price_cents > 0),
    CHECK (stock >= 0)
);
CREATE TABLE sale_orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_cents BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    UNIQUE (user_id, idempotency_key),
    FOREIGN KEY (user_id) REFERENCES app_users(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    CHECK (quantity > 0 AND quantity <= 5),
    CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED'))
);
CREATE INDEX idx_order_expiry ON sale_orders(status, expires_at);
CREATE INDEX idx_order_user ON sale_orders(user_id, created_at);
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL
);
CREATE INDEX idx_outbox_pending ON outbox_events(published_at, created_at);
CREATE TABLE notifications (
    event_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_notification_user ON notifications(user_id, created_at);
