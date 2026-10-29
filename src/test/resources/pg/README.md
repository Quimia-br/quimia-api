# Schema PostgreSQL para validação local

Usado **somente** pelos scripts de `scripts/local-validation/` em um cluster
PostgreSQL descartável criado e apagado por eles. A API nunca executa estes
arquivos e eles não são migrations: o schema de produção é administrado no
repositório `Quimia-br/quimia-bd`.

- `quimia-bd/`: cópia fiel de `src/database/sql/{ddl,logs,indexes,routines}`
  do `quimia-bd`, commit `eaccdd1` (2026-10-28). Contém `DROP TABLE`: nunca
  aplicar em banco compartilhado.
- `alteracoes-combinadas.sql`: mudanças já acordadas com o DBA que ainda não
  estão no `quimia-bd`.

Para atualizar a cópia: `scripts/local-validation/sync-schema.sh`.
