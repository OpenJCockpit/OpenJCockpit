#!/usr/bin/env bash
# Rolls the VM deployment back to the previously deployed release:
#   deploy/scripts/rollback-compose.sh                 # previous tag from .deploy-history
#   deploy/scripts/rollback-compose.sh v1.4.1          # explicit tag
#   deploy/scripts/rollback-compose.sh v1.4.1 <backup> # also restore the databases (schema rollback)
# Images are immutable, so redeploying the old tag restores the old code. Flyway migrations are
# forward-only: if the failed release migrated a schema the old code cannot read, pass the pre-deploy
# backup that deploy-compose.sh took (backups/pre-deploy-<failed tag>-*), which restore.sh applies first.
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
HISTORY="$DIR/compose/.deploy-history"
TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  [ -f "$HISTORY" ] && [ "$(wc -l < "$HISTORY")" -ge 2 ] || { echo "rollback: no previous release in $HISTORY; pass a tag"; exit 1; }
  TARGET="$(tail -n 2 "$HISTORY" | head -n 1 | awk '{print $2}')"
fi
echo "rollback: target release $TARGET"

if [ $# -ge 2 ]; then
  "$DIR/scripts/restore.sh" "$2" --yes
fi
"$DIR/scripts/deploy-compose.sh" "$TARGET"
echo "rollback: $TARGET restored"
