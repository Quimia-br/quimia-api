#!/usr/bin/env bash
# Camada C: sobe a API empacotada contra um PostgreSQL descartável com o schema
# real do quimia-bd e chama os endpoints por HTTP de verdade (curl).
#
# Uso (no seu terminal Git Bash, na raiz do repositório):
#   scripts/local-validation/smoke.sh            # usa o jar existente ou compila
#   scripts/local-validation/smoke.sh --build    # força recompilar o jar
#
# Variáveis opcionais: PG_BIN, PG_PORT (55432), APP_PORT (18080), JAVA_HOME.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

APP_PORT="${APP_PORT:-18080}"
BASE_URL="http://localhost:$APP_PORT/api/v1"
STARTUP_TIMEOUT_SECONDS=120
WORK_DIR="$(mktemp -d)"
APP_PID=""
CHECKS=0
FAILURES=0

cleanup() {
  if [[ -n "$APP_PID" ]]; then kill "$APP_PID" > /dev/null 2>&1 || true; wait "$APP_PID" 2>/dev/null || true; fi
  stop_cluster "$WORK_DIR"
}
trap cleanup EXIT

# call MÉTODO CAMINHO [CORPO_JSON] [TOKEN]: grava o status em STATUS e o corpo em BODY.
call() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o "$WORK_DIR/body" -w '%{http_code}' -X "$method" -b "$WORK_DIR/cookies" -c "$WORK_DIR/cookies")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data "$body")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  STATUS="$(curl "${args[@]}" "$BASE_URL$path")"
  BODY="$(cat "$WORK_DIR/body")"
}

# expect STATUS_ESPERADO DESCRIÇÃO [TRECHO_ESPERADO_NO_CORPO]
expect() {
  local expected="$1" description="$2" fragment="${3:-}"
  CHECKS=$((CHECKS + 1))
  if [[ "$STATUS" == "$expected" && ( -z "$fragment" || "$BODY" == *"$fragment"* ) ]]; then
    printf '  OK    %s (HTTP %s)\n' "$description" "$STATUS"
  else
    FAILURES=$((FAILURES + 1))
    printf '  FALHA %s: esperado HTTP %s%s, recebido HTTP %s\n        corpo: %s\n' \
      "$description" "$expected" "${fragment:+ contendo $fragment}" "$STATUS" "${BODY:0:300}"
  fi
}

json_field() { grep -o "\"$1\":\"[^\"]*\"" <<< "$BODY" | head -1 | cut -d'"' -f4; }

build_jar() {
  cd "$REPO_ROOT"
  if [[ "${1:-}" == "--build" ]] || ! ls target/quimia-*.jar > /dev/null 2>&1; then
    log "Compilando o jar"
    ./mvnw -B -ntp -q -DskipTests package
  fi
  JAR="$(ls target/quimia-*.jar | grep -v original | head -1)"
}

start_api() {
  log "Subindo a API ($JAR) na porta $APP_PORT"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK_DIR/jwt-private.pem" 2> /dev/null
  openssl pkey -in "$WORK_DIR/jwt-private.pem" -pubout -out "$WORK_DIR/jwt-public.pem"
  SPRING_DATASOURCE_URL="$(jdbc_url)" SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD= \
  QUIMIA_JWT_PRIVATE_KEY_PATH="$WORK_DIR/jwt-private.pem" QUIMIA_JWT_PUBLIC_KEY_PATH="$WORK_DIR/jwt-public.pem" \
  APP_AUTH_HMAC_SECRET="$(openssl rand -hex 32)" APP_AUTH_COOKIE_SECURE=false SERVER_PORT="$APP_PORT" \
    "$JAVA_HOME/bin/java" -jar "$JAR" > "$WORK_DIR/app.log" 2>&1 &
  APP_PID=$!
  local waited=0
  until grep -q "Started Application" "$WORK_DIR/app.log"; do
    if ! kill -0 "$APP_PID" 2> /dev/null || (( waited >= STARTUP_TIMEOUT_SECONDS )); then
      echo "A API não subiu. Últimas linhas do log:" >&2
      tail -40 "$WORK_DIR/app.log" >&2
      exit 1
    fi
    sleep 1; waited=$((waited + 1))
  done
  echo "  API no ar em ${waited}s (schema validado pelo Hibernate contra o banco)"
}

smoke_account() {
  log "Etapa 0: autenticação sobre o schema real"
  local email="smoke.$(date +%s)@example.com"
  call GET /usuarios/me
  expect 401 "GET /usuarios/me sem token"
  call POST /auth/register "{\"nome\":\"Smoke Usuario\",\"email\":\"$email\",\"senha\":\"SmokeSenha2026\"}"
  expect 201 "POST /auth/register" "\"nivelAcesso\":\"USUARIO\""
  call POST /auth/mobile/login "{\"email\":\"$email\",\"senha\":\"SmokeSenha2026\"}"
  expect 200 "POST /auth/mobile/login" '"refreshToken"'
  USER_TOKEN="$(json_field accessToken)"
  call GET /usuarios/me "" "$USER_TOKEN"
  expect 200 "GET /usuarios/me com token" "$email"

  call POST /empresas/auth/register "{\"nome\":\"Smoke Empresa\",\"cnpj\":\"11.222.333/0001-81\",\"email\":\"empresa.$email\",\"senha\":\"SmokeSenha2026\"}"
  expect 201 "POST /empresas/auth/register"
  call POST /empresas/auth/login "{\"email\":\"empresa.$email\",\"senha\":\"SmokeSenha2026\"}"
  expect 200 "POST /empresas/auth/login" '"empresa"'
  EMPRESA_TOKEN="$(json_field accessToken)"
  CHECKS=$((CHECKS + 1))
  if grep -q quimia_rt "$WORK_DIR/cookies"; then echo "  OK    cookie de refresh da empresa emitido"; else FAILURES=$((FAILURES + 1)); echo "  FALHA cookie quimia_rt não foi emitido"; fi
  call GET /empresas/me "" "$EMPRESA_TOKEN"
  expect 200 "GET /empresas/me" '"cnpj":"11222333000181"'
  call GET /usuarios/me "" "$EMPRESA_TOKEN"
  expect 403 "token de empresa em rota de usuário"
}

check_database() {
  log "Conferindo o que a API gravou no PostgreSQL"
  "$PG_BIN/psql" -h localhost -p "$PG_PORT" -U postgres -d "$PG_DB" -tA -c \
    "SELECT '  usuarios: ' || (SELECT count(*) FROM usuario) || ' | empresas: ' || (SELECT count(*) FROM empresa) || ' | senha com {bcrypt}: ' || (SELECT count(*) FROM usuario WHERE senha LIKE '{bcrypt}%');"
}

use_jdk21
build_jar "${1:-}"
start_cluster "$WORK_DIR"
apply_schema
start_api
smoke_account
check_database

log "Resultado: $CHECKS verificações, $FAILURES falha(s)"
(( FAILURES == 0 ))
