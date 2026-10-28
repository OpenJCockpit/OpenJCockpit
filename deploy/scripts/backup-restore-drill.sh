#!/usr/bin/env bash
# Recovery drill: proves backup.sh + restore.sh round-trip the state, without touching a real deployment.
#   deploy/scripts/backup-restore-drill.sh
# Starts a throwaway PostgreSQL initialised by the production init script, writes marker data into every
# database and into a workflow-definitions volume, takes a backup, destroys the data, restores, and checks
# that every marker is back. Run it in CI (infra-ci) and before each release; run the same backup.sh /
# restore.sh pair monthly against a staging copy of production (runbook).
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="ojc-drill-$$"
PG="${PROJECT}-postgres"
WORK="$(mktemp -d)"
IMAGE="postgres:16-alpine@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea"
cleanup() { docker rm -f "$PG" >/dev/null 2>&1 || true; docker volume rm "${PROJECT}_workflow-definitions" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

pw() { LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24; }
docker volume create "${PROJECT}_workflow-definitions" >/dev/null
docker run -d --name "$PG" \
  -e POSTGRES_USER=drill_super -e POSTGRES_PASSWORD="$(pw)" \
  -e KEYCLOAK_DB_PASSWORD="$(pw)" -e OPENJCOCKPIT_DB_PASSWORD="$(pw)" -e EMBABEL_DB_PASSWORD="$(pw)" -e LITELLM_DB_PASSWORD="$(pw)" \
  -v "$DIR/../compose/postgres/init-databases.sh:/docker-entrypoint-initdb.d/10-init-databases.sh:ro" \
  "$IMAGE" >/dev/null
for _ in $(seq 60); do
  docker exec "$PG" pg_isready -U drill_super -d embabel >/dev/null 2>&1 && docker exec "$PG" psql -U drill_super -d litellm -tAc 'select 1' >/dev/null 2>&1 && break
  sleep 1
done

psql() { docker exec -i "$PG" psql -v ON_ERROR_STOP=1 -U drill_super -tA "$@"; }
for db in keycloak openjcockpit embabel litellm; do
  owner="$(psql -d postgres -c "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='$db'")"
  [ "$owner" = "${db}_app" ] || { echo "drill: FAIL $db owned by '$owner'"; exit 1; }
  psql -d "$db" -c "SET ROLE ${db}_app; CREATE TABLE drill_marker(id int primary key, note text); INSERT INTO drill_marker VALUES (1, '$db-before-backup');" >/dev/null
done
docker run --rm -v "${PROJECT}_workflow-definitions:/data" "$IMAGE" sh -c 'mkdir -p /data/decision-logs && echo audit-entry > /data/decision-logs/drill.jsonl'

export PG_CONTAINER="$PG" PG_USER=drill_super BACKUP_DIR="$WORK/backups" COMPOSE_PROJECT="$PROJECT" ENV_FILE=/dev/null METRICS_TEXTFILE_DIR=""
"$DIR/backup.sh" drill >/dev/null
BACKUP="$(ls -d "$WORK"/backups/drill-*)"

# Disaster: data changed and lost after the backup.
for db in keycloak openjcockpit embabel litellm; do psql -d "$db" -c "DROP TABLE drill_marker" >/dev/null; done
docker run --rm -v "${PROJECT}_workflow-definitions:/data" "$IMAGE" sh -c 'rm -rf /data/*'

APP_SERVICES="" "$DIR/restore.sh" "$BACKUP" --yes >/dev/null

failures=0
for db in keycloak openjcockpit embabel litellm; do
  got="$(psql -d "$db" -c 'SELECT note FROM drill_marker WHERE id=1' 2>/dev/null || true)"
  owner="$(psql -d "$db" -c "SELECT tableowner FROM pg_tables WHERE tablename='drill_marker'" 2>/dev/null || true)"
  if [ "$got" = "$db-before-backup" ] && [ "$owner" = "${db}_app" ]; then echo "drill: PASS $db restored"; else echo "drill: FAIL $db ($got/$owner)"; failures=$((failures+1)); fi
done
if docker run --rm -v "${PROJECT}_workflow-definitions:/data:ro" "$IMAGE" grep -q audit-entry /data/decision-logs/drill.jsonl; then
  echo "drill: PASS workflow-definitions (audit trail) restored"
else echo "drill: FAIL workflow-definitions"; failures=$((failures+1)); fi
# A tampered backup must be refused.
echo tampered >> "$BACKUP/embabel.dump"
if APP_SERVICES="" "$DIR/restore.sh" "$BACKUP" --yes >/dev/null 2>&1; then echo "drill: FAIL tampered backup accepted"; failures=$((failures+1)); else echo "drill: PASS tampered backup refused"; fi

[ "$failures" -eq 0 ] || { echo "drill: $failures failure(s)"; exit 1; }
echo "drill: backup/restore round trip OK"
