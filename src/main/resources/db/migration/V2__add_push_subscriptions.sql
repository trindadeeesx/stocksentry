CREATE TABLE push_subscriptions
(
    id          UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    tenant_id   UUID      NOT NULL REFERENCES tenants (id),
    endpoint    TEXT      NOT NULL UNIQUE,
    p256dh      TEXT      NOT NULL,
    auth_key    TEXT      NOT NULL,
    device_name VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_tenant_id ON push_subscriptions (tenant_id);