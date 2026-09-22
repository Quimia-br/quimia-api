CREATE TABLE IF NOT EXISTS usuario (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    data_nasc DATE,
    nivel_acesso VARCHAR(50) NOT NULL DEFAULT 'USUARIO'
        CONSTRAINT chk_usuario_nivel_acesso CHECK (nivel_acesso IN ('USUARIO', 'EMPRESA', 'ADMIN')),
    ultima_sessao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_usuario_email UNIQUE (email)
);
CREATE TABLE IF NOT EXISTS localizacao_usuario (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario UUID NOT NULL,
    cep VARCHAR(9),
    estado VARCHAR(2),
    bairro VARCHAR(255),
    rua VARCHAR(255),
    numero INTEGER,
    complemento VARCHAR(255),
    CONSTRAINT uq_localizacao_usuario UNIQUE (id_usuario),
    CONSTRAINT fk_localizacao_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id) ON DELETE CASCADE
);
