DROP TABLE IF EXISTS stock_movements;
DROP INDEX IF EXISTS idx_movements_product_id;
DROP INDEX IF EXISTS idx_movements_tenant_id;
DROP INDEX IF EXISTS idx_movements_created_at;

DROP INDEX IF EXISTS idx_push_subscriptions_tenant_id;
ALTER TABLE push_subscriptions DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE alert_configs DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_alerts_tenant_id;
ALTER TABLE alerts DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_tenant_id_sku_key;
ALTER TABLE products DROP COLUMN IF EXISTS tenant_id;
DROP INDEX IF EXISTS idx_products_tenant_id;
ALTER TABLE products
    ADD CONSTRAINT products_sku_key UNIQUE (sku);

ALTER TABLE users DROP COLUMN IF EXISTS tenant_id;

DROP TABLE IF EXISTS tenants;