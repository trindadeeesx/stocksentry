CREATE TABLE app_settings (
    id            SMALLINT PRIMARY KEY DEFAULT 1,
    sync_interval_ms BIGINT NOT NULL DEFAULT 300000,
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO app_settings (id, sync_interval_ms) VALUES (1, 300000);
