#!/usr/bin/env bash
# Camada B: roda a suíte de testes contra um PostgreSQL descartável com o
# schema real do quimia-bd, com ddl-auto=validate (como em produção).
#
# Uso: scripts/local-validation/run-pg-tests.sh [padrão -Dtest do Surefire]
#   ex.: scripts/local-validation/run-pg-tests.sh 'Account*Test,Empresa*Test'

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TEST_FILTER="${1:-}"
WORK_DIR="$(mktemp -d)"
trap 'stop_cluster "$WORK_DIR"' EXIT

use_jdk21
start_cluster "$WORK_DIR"
apply_schema

log "Rodando os testes contra $(jdbc_url)"
cd "$REPO_ROOT"
args=(-B -ntp -o test -Djacoco.skip=true
  "-Dspring.datasource.url=$(jdbc_url)"
  -Dspring.datasource.driver-class-name=org.postgresql.Driver
  -Dspring.datasource.username=postgres
  -Dspring.datasource.password=
  -Dspring.jpa.hibernate.ddl-auto=validate)
if [[ -n "$TEST_FILTER" ]]; then
  args+=("-Dtest=$TEST_FILTER" -Dsurefire.failIfNoSpecifiedTests=false)
fi
./mvnw "${args[@]}"

# Prova de que os testes gravaram neste PostgreSQL (e não no H2 padrão):
# o trigger trg_auditoria_usuario registra cada insert em usuario.
log "Conferindo que os testes usaram este PostgreSQL"
"$PG_BIN/psql" -h localhost -p "$PG_PORT" -U postgres -d "$PG_DB" -tA -c \
  "SELECT 'linhas de usuario auditadas pelo trigger do banco: ' || count(*) FROM log_auditoria WHERE tabela_afetada = 'usuario';"
