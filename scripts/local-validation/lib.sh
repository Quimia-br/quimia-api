#!/usr/bin/env bash
# Funções compartilhadas pelos scripts de validação local.
# Sempre cria um cluster PostgreSQL NOVO num diretório temporário; nunca
# recebe conexão de fora, para não haver risco de aplicar o DDL (que tem
# DROP TABLE) em um banco compartilhado.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCHEMA_DIR="$REPO_ROOT/src/test/resources/pg"
PG_BIN="${PG_BIN:-/c/Program Files/PostgreSQL/18/bin}"
PG_PORT="${PG_PORT:-55432}"
PG_DB="quimia"

# Ordem de aplicação do DDL do quimia-bd.
SCHEMA_FILES=(
  "quimia-bd/ddl/tables/create_tables.sql"
  "quimia-bd/logs/log_auditoria.sql"
  "quimia-bd/logs/log_rpa_execucao.sql"
  "quimia-bd/logs/sessao_acesso.sql"
  "quimia-bd/ddl/constraints/foreign_keys.sql"
)

log() { printf '\n==> %s\n' "$*"; }

use_jdk21() {
  local candidate="${JAVA_HOME:-}"
  if [[ -z "$candidate" || ! -x "$candidate/bin/java" ]] || ! "$candidate/bin/java" -version 2>&1 | grep -q '"21'; then
    candidate="/c/Program Files/Java/jdk-21.0.11"
  fi
  if ! "$candidate/bin/java" -version 2>&1 | grep -q '"21'; then
    echo "JDK 21 não encontrado. Defina JAVA_HOME apontando para um JDK 21." >&2
    exit 1
  fi
  export JAVA_HOME="$candidate"
  export PATH="$JAVA_HOME/bin:$PATH"
}

# start_cluster <diretório>
start_cluster() {
  local dir="$1"
  [[ -x "$PG_BIN/initdb" || -x "$PG_BIN/initdb.exe" ]] || { echo "PostgreSQL não encontrado em $PG_BIN (defina PG_BIN)." >&2; exit 1; }
  log "Criando PostgreSQL descartável em $dir (porta $PG_PORT)"
  "$PG_BIN/initdb" -D "$dir/data" -U postgres --auth=trust -E UTF8 --locale=C > "$dir/initdb.log" 2>&1
  # Saída redirecionada para o processo do servidor não prender o terminal.
  "$PG_BIN/pg_ctl" -D "$dir/data" -o "-p $PG_PORT" -l "$dir/postgres.log" -w start > /dev/null 2>&1
  "$PG_BIN/createdb" -h localhost -p "$PG_PORT" -U postgres "$PG_DB"
}

stop_cluster() {
  local dir="$1"
  if [[ -d "$dir/data" ]]; then
    "$PG_BIN/pg_ctl" -D "$dir/data" -m fast stop > /dev/null 2>&1 || true
  fi
  rm -rf "$dir"
}

psql_file() {
  # Os arquivos são UTF-8; no Windows o psql assumiria WIN1252.
  PGCLIENTENCODING=UTF8 PGOPTIONS='-c client_min_messages=warning' \
    "$PG_BIN/psql" -h localhost -p "$PG_PORT" -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 -q -f "$1" > /dev/null
}

apply_schema() {
  log "Aplicando o DDL do quimia-bd e as alterações combinadas"
  local file
  for file in "${SCHEMA_FILES[@]}"; do psql_file "$SCHEMA_DIR/$file"; done
  for file in "$SCHEMA_DIR"/quimia-bd/indexes/*.sql \
              "$SCHEMA_DIR"/quimia-bd/routines/functions/*.sql \
              "$SCHEMA_DIR"/quimia-bd/routines/procedures/*.sql \
              "$SCHEMA_DIR"/quimia-bd/routines/triggers/*.sql; do
    psql_file "$file"
  done
  psql_file "$SCHEMA_DIR/alteracoes-combinadas.sql"
  if [[ -n "${1:-}" ]]; then psql_file "$1"; fi
}

jdbc_url() { echo "jdbc:postgresql://localhost:$PG_PORT/$PG_DB"; }
