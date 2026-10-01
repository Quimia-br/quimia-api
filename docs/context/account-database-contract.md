# Contrato do banco — `account`

O PostgreSQL externo é responsável por criar, alterar e versionar o schema. A
fonte de verdade é o DDL `quimia_ddl_com_constraints.sql` (fora do repositório,
decisão 21). A API não inclui Flyway nem executa DDL e, em produção, usa
`ddl-auto: validate`: se faltar uma tabela ou coluna mapeada, ou se um tipo for
incompatível, a aplicação não inicia.

Desde a decisão 21, o módulo usa **somente** tabelas que existem nesse DDL. Ele
não depende de `refresh_token`, `recuperacao_senha`, `auditoria_autenticacao`
nem de colunas de bloqueio. Se essas estruturas existirem no banco por causa de
versões antigas, a API não as lê nem as altera.

## Tabelas e colunas mapeadas

| Tabela | Colunas usadas pela API | Observações do DDL |
|---|---|---|
| `usuario` | `id UUID`, `nome VARCHAR(255)`, `email VARCHAR(255)`, `data_nasc DATE`, `foto_url VARCHAR(450)`, `senha VARCHAR(100)`, `nivel_acesso VARCHAR(50)`, `ultima_sessao TIMESTAMPTZ` | `email` UNIQUE NOT NULL; `senha` NOT NULL; `nivel_acesso` CHECK em `usuario/empresa/admin`, gravado em minúsculas |
| `empresa` | `id INTEGER identity`, `nome VARCHAR(255)`, `email VARCHAR(255)`, `cnpj VARCHAR(20)`, `ativo BOOLEAN`, `senha VARCHAR(100)`, `foto_url VARCHAR(200)` | `email` **sem** UNIQUE: a API garante unicidade (comparação sem distinção de maiúsculas) e recusa login quando há duplicidade legada; `cnpj` UNIQUE e anulável; `ativo` anulável, e NULL vale como ativo |
| `localizacao_usuario` | `id INTEGER identity`, `id_usuario UUID`, `cep VARCHAR(9)`, `estado VARCHAR(2)`, `bairro`, `rua`, `numero INTEGER`, `complemento` | `UNIQUE(id_usuario)`: um endereço por usuário. Não há coluna de cidade |

## Formato dos dados gravados

- `senha` (em `usuario` e `empresa`): `{bcrypt}` seguido de hash BCrypt de
  custo 12, com 68 caracteres. Valores legados sem prefixo são aceitos no login
  somente se forem BCrypt; qualquer outro formato não confere e exige
  recuperação de senha.
- Usuários criados por login social (Firebase) recebem um hash BCrypt de uma
  senha aleatória descartada, porque `senha` é NOT NULL.
- `email` é gravado com `trim` e em minúsculas.
- `cnpj` é gravado sem pontuação e em maiúsculas: 14 caracteres, aceitando o
  formato alfanumérico da Receita Federal.
- `cep` é gravado como `00000-000`; `estado`, como UF em maiúsculas.

## Estado fora do banco

| Necessidade | Onde fica | Consequência |
|---|---|---|
| Sessão (refresh) | JWT assinado e autocontido | Não há revogação individual; trocar a senha invalida todos os refresh |
| Bloqueio por falhas, cooldown e tentativas de código | Memória da instância | Os contadores zeram ao reiniciar e não são compartilhados entre réplicas |
| Desafio e redefinição de senha | Tokens assinados devolvidos ao cliente | Ficam inválidos quando a senha muda (uso único) |
| Auditoria | Log `account.audit` | Não há trilha consultável no banco |

## Publicação

Antes de uma release, confira com consultas somente leitura no banco de destino
as tabelas, colunas e tipos acima, além das permissões de leitura e escrita em
`usuario`, `empresa` e `localizacao_usuario`. Não use `ddl-auto=update/create`
em produção. Sem schema comprovado e sem o smoke check passando após a
inicialização, a release não está validada para deploy.

A validação local de 2026-10-01 aplicou o DDL em um PostgreSQL 18 descartável.
A API iniciou com `validate` e os testes de integração de `account` passaram
nesse banco.
