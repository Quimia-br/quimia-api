CREATE TABLE IF NOT EXISTS recuperacao_senha (
    id UUID PRIMARY KEY,
    id_usuario UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    codigo_hash VARCHAR(64),
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas BETWEEN 0 AND 5),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    codigo_consumido_em TIMESTAMP WITH TIME ZONE,
    token_reset_hash VARCHAR(64),
    token_reset_expira_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE,
    revogado_em TIMESTAMP WITH TIME ZONE,
    CHECK ((codigo_consumido_em IS NULL) = (codigo_hash IS NOT NULL)),
    CHECK ((token_reset_hash IS NULL) = (token_reset_expira_em IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_recuperacao_senha_usuario_criado
    ON recuperacao_senha (id_usuario, criado_em DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recuperacao_senha_token_reset_hash
    ON recuperacao_senha (token_reset_hash);
