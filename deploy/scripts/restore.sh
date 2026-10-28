#!/usr/bin/env bash
# Restores a backup taken by backup.sh into the VM deployment:
#   deploy/scripts/restore.sh <backup-dir> [--yes]
# Stops the application tier, verifies the MANIFEST checksums, recreates each database from its dump,
# restores the workflow-definitions volume, and starts the application tier again (deploy-compose.sh
# or rollback-compose.sh then runs the smoke test). Destructive for the current data: asks first.
set -euo pipefail

SRC="${1:?usage: restore.sh <backup-dir> [--yes]}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$DIR/compose/.env}"
envval() { [ -f "$ENV_FILE" ] && grep -E "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }
PROJECT="${COMPOSE_PROJECT:-openjcockpit}"
PG_CONTAINER="${PG_CONTAINER:-$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=postgres")}"
PG_USER="${PG_USER:-$(envval POSTGRES_SUPERUSER)}"
APP_SERVICES="${APP_SERVICES:-caddy dashboard ai-control-service embabel-agent-service embabel-agent-runtime litellm keycloak git-mcp-server}"
DATABASES="$(grep -E '^databases=' "$SRC/MANIFEST" | cut -d= -f2-)"

[ -n "$PG_CONTAINER" ] || { echo "restore: postgres container of project '$PROJECT' is not running"; exit 1; }
(cd "$SRC" && grep -E '^[0-9a-f]{64} ' MANIFEST | { sha256sum -c --quiet 2>/dev/null || shasum -a 256 -c --quiet; }) \
  || { echo "restore: checksum mismatch in $SRC"; exit 1; }
echo "restore: $SRC ($(grep -E '^release=' "$SRC/MANIFEST")) -> databases: $DATABASES"

if [ "${2:-}" != "--yes" ]; then
  read -r -p "This REPLACES the current data. Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "restore: aborted"; exit 1; }
fi

containers_of() { for s in $APP_SERVICES; do docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=$s"; done; }
running="$(containers_of)"
[ -z "$running" ] || docker stop $running > /dev/null

for db in $DATABASES; do
  owner="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -tAc \
    "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='$db'")"
  owner="${owner:-${db}_app}"
  docker exec "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d postgres -q \
    -c "DROP DATABASE IF EXISTS \"$db\" WITH (FORCE)" -c "CREATE DATABASE \"$db\" OWNER \"$owner\""
  docker exec -i "$PG_CONTAINER" pg_restore -U "$PG_USER" -d "$db" --no-owner --role="$owner" --exit-on-error < "$SRC/$db.dump"
  echo "restore: database $db restored (owner $owner)"
done

if [ -f "$SRC/workflow-definitions.tar.gz" ]; then
  docker run --rm -v "${PROJECT}_workflow-definitions:/data" -v "$(cd "$SRC" && pwd):/in:ro" \
    postgres:16-alpine@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea \
    sh -c 'find /data -mindepth 1 -delete && tar -C /data -xzf /in/workflow-definitions.tar.gz && chown -R 10001:10001 /data'
  echo "restore: workflow-definitions volume restored"
fi

[ -z "$running" ] || docker start $running > /dev/null
echo "restore: done"
