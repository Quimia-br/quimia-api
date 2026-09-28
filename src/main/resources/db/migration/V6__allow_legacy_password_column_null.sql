-- Older database schemas still require the legacy `senha` column, while the
-- current application writes credentials to `senha_hash`.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS senha VARCHAR(255);
ALTER TABLE usuario ALTER COLUMN senha DROP NOT NULL;
