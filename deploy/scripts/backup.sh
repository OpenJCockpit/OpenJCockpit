#!/usr/bin/env bash
# Consistent backup of the VM deployment's state:
#   deploy/scripts/backup.sh [label]
# Contents (one directory per run under $BACKUP_DIR, default /var/backups/openjcockpit):
#   globals.sql            roles (pg_dumpall --globals-only)
#   <db>.dump              keycloak, openjcockpit, embabel, litellm (pg_dump custom format, consistent snapshot)
#   workflow-definitions.tar.gz   workflow definitions + the policy/approval decision audit trails
#   MANIFEST               release tag, timestamp, sha256 of every file
# Transient volumes (job/git workspaces) and TLS certificates are not backed up (re-created on demand).
# NOT included on purpose: deploy/compose/.env (secrets) -- back it up separately, encrypted.
# Writes openjcockpit_backup_last_success_timestamp_seconds for the BackupTooOld alert.
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$DIR/compose/.env}"
envval() { [ -f "$ENV_FILE" ] && grep -E "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }

PROJECT="${COMPOSE_PROJECT:-openjcockpit}"
PG_CONTAINER="${PG_CONTAINER:-$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=postgres")}"
PG_USER="${PG_USER:-$(envval POSTGRES_SUPERUSER)}"
BACKUP_DIR="${BACKUP_DIR:-$(envval BACKUP_DIR)}"; BACKUP_DIR="${BACKUP_DIR:-/var/backups/openjcockpit}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-$(envval BACKUP_RETENTION_DAYS)}"; RETENTION_DAYS="${RETENTION_DAYS:-14}"
METRICS_DIR="${METRICS_TEXTFILE_DIR:-$(envval METRICS_TEXTFILE_DIR)}"
DATABASES="${DATABASES:-keycloak openjcockpit embabel litellm}"
TAG="$(envval OPENJCOCKPIT_IMAGE_TAG)"

[ -n "$PG_CONTAINER" ] || { echo "backup: postgres container of project '$PROJECT' is not running"; exit 1; }
[ -n "$PG_USER" ] || { echo "backup: POSTGRES_SUPERUSER unknown (set PG_USER or $ENV_FILE)"; exit 1; }

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$BACKUP_DIR/${1:-scheduled}-$STAMP"
mkdir -p "$OUT"
chmod 700 "$OUT"
trap 'echo "backup: FAILED, removing partial $OUT"; rm -rf "$OUT"' ERR

docker exec "$PG_CONTAINER" pg_dumpall -U "$PG_USER" --globals-only > "$OUT/globals.sql"
for db in $DATABASES; do
  docker exec "$PG_CONTAINER" pg_dump -U "$PG_USER" -Fc --no-owner "$db" > "$OUT/$db.dump"
  # Prove the archive is readable now, not on the day it is needed.
  docker exec -i "$PG_CONTAINER" pg_restore --list > /dev/null < "$OUT/$db.dump"
done
docker run --rm -v "${PROJECT}_workflow-definitions:/data:ro" -v "$OUT:/out" \
  postgres:16-alpine@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea \
  tar -C /data -czf /out/workflow-definitions.tar.gz .

{
  echo "release=${TAG:-unknown}"
  echo "created=$STAMP"
  echo "databases=$DATABASES"
  (cd "$OUT" && sha256sum ./* 2>/dev/null || shasum -a 256 ./*) | grep -v MANIFEST
} > "$OUT/MANIFEST"
chmod 600 "$OUT"/*
trap - ERR

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -exec rm -rf {} +

if [ -n "$METRICS_DIR" ] && [ -d "$METRICS_DIR" ]; then
  printf '# HELP openjcockpit_backup_last_success_timestamp_seconds Last successful backup.\n# TYPE openjcockpit_backup_last_success_timestamp_seconds gauge\nopenjcockpit_backup_last_success_timestamp_seconds %s\n' \
    "$(date +%s)" > "$METRICS_DIR/openjcockpit_backup.prom.$$" && mv "$METRICS_DIR/openjcockpit_backup.prom.$$" "$METRICS_DIR/openjcockpit_backup.prom"
fi
echo "backup: $OUT"
