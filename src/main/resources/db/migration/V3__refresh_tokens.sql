CREATE TABLE IF NOT EXISTS refresh_token (
    id UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    familia_id UUID NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    revogado_em TIMESTAMP WITH TIME ZONE,
    substituido_por UUID,
    ultimo_uso_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_refresh_token_usuario ON refresh_token (id_usuario);
CREATE INDEX IF NOT EXISTS idx_refresh_token_familia ON refresh_token (familia_id);
CREATE TABLE IF NOT EXISTS auditoria_autenticacao (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario UUID,
    evento VARCHAR(60) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON auditoria_autenticacao (id_usuario);
