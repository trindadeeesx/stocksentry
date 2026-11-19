-- Coluna de controle de concorrência otimista (@Version em Product).
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
