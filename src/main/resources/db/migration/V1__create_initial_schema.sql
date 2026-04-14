CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    name          VARCHAR(255) NOT NULL,
    sku           VARCHAR(100) NOT NULL,
    unit          VARCHAR(10) NOT NULL,
    current_stock NUMERIC(10,3) NOT NULL DEFAULT 0,
    min_stock     NUMERIC(10,3) NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, sku)
);

CREATE TABLE stock_movements (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products(id),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    user_id       UUID NOT NULL REFERENCES users(id),
    type          VARCHAR(20) NOT NULL,
    quantity      NUMERIC(10,3) NOT NULL,
    stock_before  NUMERIC(10,3) NOT NULL,
    stock_after   NUMERIC(10,3) NOT NULL,
    reason        VARCHAR(500),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_configs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    type        VARCHAR(10) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE alerts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    product_id   UUID NOT NULL REFERENCES products(id),
    type         VARCHAR(10) NOT NULL,
    destination  VARCHAR(255) NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    status       VARCHAR(10) NOT NULL
);

CREATE INDEX idx_products_tenant_id ON products(tenant_id);
CREATE INDEX idx_movements_product_id ON stock_movements(product_id);
CREATE INDEX idx_movements_tenant_id ON stock_movements(tenant_id);
CREATE INDEX idx_movements_created_at ON stock_movements(created_at);
CREATE INDEX idx_alerts_tenant_id ON alerts(tenant_id);
CREATE INDEX idx_alerts_product_id ON alerts(product_id);