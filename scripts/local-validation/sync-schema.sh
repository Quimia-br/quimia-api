#!/usr/bin/env bash
# Compara a cópia do schema em src/test/resources/pg/quimia-bd com o
# repositório Quimia-br/quimia-bd (branch main) e, com --apply, atualiza.
# Requer o GitHub CLI (gh) autenticado.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

REMOTE_PREFIX="src/database/sql/"
TARGET="$SCHEMA_DIR/quimia-bd"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

commit="$(gh api repos/Quimia-br/quimia-bd/commits/main --jq '.sha[0:7]')"
log "Baixando quimia-bd@$commit"
while read -r path; do
  relative="${path#"$REMOTE_PREFIX"}"
  mkdir -p "$WORK_DIR/$(dirname "$relative")"
  gh api "repos/Quimia-br/quimia-bd/contents/$path" -H "Accept: application/vnd.github.raw" > "$WORK_DIR/$relative"
done < <(gh api "repos/Quimia-br/quimia-bd/git/trees/main?recursive=1" \
           --jq '.tree[] | select(.type=="blob") | .path' \
         | grep -E "^${REMOTE_PREFIX}(ddl/tables/create_tables|ddl/constraints/|logs/|indexes/|routines/).*\.sql$")

if diff -r -q "$TARGET" "$WORK_DIR" > /dev/null; then
  log "Cópia local já está igual ao quimia-bd@$commit"
  exit 0
fi
diff -r -u "$TARGET" "$WORK_DIR" || true

if [[ "${1:-}" == "--apply" ]]; then
  rm -rf "$TARGET" && cp -r "$WORK_DIR" "$TARGET"
  log "Atualizado para quimia-bd@$commit. Atualize o commit citado em src/test/resources/pg/README.md e revise alteracoes-combinadas.sql."
else
  log "Há diferenças. Rode com --apply para atualizar a cópia."
fi
