#!/usr/bin/env bash
# Sobe um PostgreSQL descartável (schema real do quimia-bd + dados de exemplo) e a API, e deixa
# rodando em primeiro plano. Ctrl-C encerra a API e apaga o banco.
#
# Uso (Git Bash, na raiz do repositório): make run-local [PORT=8080]
# Variáveis opcionais: SERVER_PORT (8080), PG_PORT (55432), PG_BIN, JAVA_HOME.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

APP_PORT="${SERVER_PORT:-8080}"
WORK_DIR="$(mktemp -d)"
APP_PID=""

cleanup() {
  if [[ -n "$APP_PID" ]]; then kill "$APP_PID" > /dev/null 2>&1 || true; wait "$APP_PID" 2> /dev/null || true; fi
  stop_cluster "$WORK_DIR"
}
trap cleanup EXIT INT TERM

use_jdk21
cd "$REPO_ROOT"
if ! ls target/quimia-*.jar > /dev/null 2>&1; then
  log "Compilando o jar"
  ./mvnw -B -ntp -q -DskipTests package
fi
JAR="$(ls target/quimia-*.jar | grep -v original | head -1)"

start_cluster "$WORK_DIR"
apply_schema "$SCHEMA_DIR/seed-smoke-catalogo.sql"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK_DIR/jwt-private.pem" 2> /dev/null
openssl pkey -in "$WORK_DIR/jwt-private.pem" -pubout -out "$WORK_DIR/jwt-public.pem"

log "API em http://localhost:$APP_PORT (Ctrl-C encerra e apaga o banco descartável)"
SPRING_DATASOURCE_URL="$(jdbc_url)" SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD= \
QUIMIA_JWT_PRIVATE_KEY_PATH="$WORK_DIR/jwt-private.pem" QUIMIA_JWT_PUBLIC_KEY_PATH="$WORK_DIR/jwt-public.pem" \
APP_AUTH_HMAC_SECRET="$(openssl rand -hex 32)" APP_AUTH_COOKIE_SECURE=false SERVER_PORT="$APP_PORT" \
  "$JAVA_HOME/bin/java" -jar "$JAR" &
APP_PID=$!
wait "$APP_PID"
