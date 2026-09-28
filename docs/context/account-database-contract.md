# Contrato do banco — `account`

O PostgreSQL externo é responsável por criar, alterar e versionar o schema. A
API não inclui Flyway nem executa DDL. Em produção, Hibernate usa `validate`:
se faltar uma tabela, coluna ou tipo incompatível mapeado, a aplicação falha ao
iniciar. Essa validação não confirma todas as constraints, índices e colunas
legadas que a aplicação não mapeia; compare também os requisitos abaixo com o
schema real antes de publicar uma release.

## Tabelas e colunas usadas pelo módulo

Tipos PostgreSQL esperados:

| Tabela | Colunas exigidas pelo mapeamento da API |
|---|---|
| `usuario` | `id UUID`, `nome VARCHAR(255)`, `email VARCHAR(255)`, `data_nasc DATE`, `nivel_acesso VARCHAR(50)`, `ultima_sessao TIMESTAMPTZ`, `senha_hash VARCHAR(255)`, `falhas_login INTEGER`, `bloqueado_ate TIMESTAMPTZ`, `ultima_falha_em TIMESTAMPTZ` |
| `refresh_token` | `id UUID`, `id_usuario UUID`, `token_hash VARCHAR(64)`, `familia_id UUID`, `expira_em TIMESTAMPTZ`, `revogado_em TIMESTAMPTZ`, `substituido_por UUID`, `ultimo_uso_em TIMESTAMPTZ`, `criado_em TIMESTAMPTZ` |
| `recuperacao_senha` | `id UUID`, `id_usuario UUID`, `codigo_hash VARCHAR(64)`, `expira_em TIMESTAMPTZ`, `tentativas INTEGER`, `criado_em TIMESTAMPTZ`, `codigo_consumido_em TIMESTAMPTZ`, `token_reset_hash VARCHAR(64)`, `token_reset_expira_em TIMESTAMPTZ`, `concluido_em TIMESTAMPTZ`, `revogado_em TIMESTAMPTZ` |
| `auditoria_autenticacao` | `id BIGINT` identity, `id_usuario UUID`, `evento VARCHAR(60)`, `criado_em TIMESTAMPTZ` |

`id` é chave primária em todas as tabelas. `usuario.email` deve ser único e
obrigatório; `nome`, `nivel_acesso` e `falhas_login` são obrigatórios.
`refresh_token.token_hash` deve ser único; seus campos de identidade, hash,
família, expiração e criação são obrigatórios. `recuperacao_senha.tentativas`
deve aceitar somente valores de 0 a 5; os pares código consumido/hash e token
de reset/hash-expiração precisam manter a consistência esperada pelo domínio.
`auditoria_autenticacao.evento` e `criado_em` são obrigatórios.

Relações esperadas: `refresh_token.id_usuario` referencia `usuario.id` com
exclusão em cascata; `recuperacao_senha.id_usuario` referencia `usuario.id`
com exclusão em cascata; `auditoria_autenticacao.id_usuario` referencia
`usuario.id` com `ON DELETE SET NULL`. Índices usados para consulta devem cobrir
`refresh_token.id_usuario`, `refresh_token.familia_id`,
`recuperacao_senha(id_usuario, criado_em DESC)`,
`recuperacao_senha.token_reset_hash` (único) e
`auditoria_autenticacao.id_usuario`.

## Compatibilidade com o schema legado

- A aplicação grava credenciais em `usuario.senha_hash`. Se existir uma coluna
  legada `usuario.senha` sem valor padrão, ela deve aceitar `NULL`; novos
  inserts não a preenchem. Valores legados existentes devem ser preservados.
- Colunas antigas de verificação de email podem permanecer; a API não as lê nem
  as altera.
- `localizacao_usuario` e outras tabelas do produto ficam sob administração do
  banco e não são removidas nem alteradas pela API.
- Não remova tabelas, colunas, constraints ou a tabela histórica
  `flyway_schema_history` como parte desta mudança. O histórico antigo pode
  permanecer sem Flyway instalado.

## Publicação

Antes de uma release, execute consultas somente leitura no banco de destino
para conferir colunas, tipos, constraints, índices e permissões de escrita nas
tabelas usadas. Em particular, `usuario.bloqueado_ate` precisa existir e a
coluna legada `usuario.senha` não pode rejeitar os inserts atuais. Não use
`ddl-auto=update/create` na produção. Sem comprovação do schema e do smoke check
após inicialização, a release não está validada para deploy.
