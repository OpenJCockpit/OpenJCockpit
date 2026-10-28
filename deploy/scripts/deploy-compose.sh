#!/usr/bin/env bash
# Reproducible, re-runnable production deployment on a VM/VPS:
#   deploy/scripts/deploy-compose.sh            # deploy OPENJCOCKPIT_IMAGE_TAG from deploy/compose/.env
#   deploy/scripts/deploy-compose.sh v1.4.2     # deploy that tag (written into .env first)
# Steps: validate env -> pre-upgrade backup -> pull pinned images -> start in dependency order ->
# wait for health -> smoke test -> record the tag in .deploy-history (rollback-compose.sh reads it).
# A failed health wait or smoke test exits non-zero and leaves the previous tag recorded for rollback.
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_DIR="$DIR/compose"
ENV_FILE="$COMPOSE_DIR/.env"
HISTORY="$COMPOSE_DIR/.deploy-history"
compose() { docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_DIR/docker-compose.prod.yml" --env-file "$ENV_FILE" "$@"; }

if [ $# -ge 1 ]; then
  sed -i.bak "s/^OPENJCOCKPIT_IMAGE_TAG=.*/OPENJCOCKPIT_IMAGE_TAG=$1/" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
fi

"$DIR/scripts/validate-env.sh" "$ENV_FILE"
compose config --quiet
TAG="$(grep -E '^OPENJCOCKPIT_IMAGE_TAG=' "$ENV_FILE" | cut -d= -f2-)"
echo "deploy: release $TAG"

# Pre-upgrade backup whenever a database already exists (migrations are forward-only).
if compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  "$DIR/scripts/backup.sh" "pre-deploy-$TAG"
fi

compose pull --quiet
# Data and identity first, then the bootstrap job (idempotent), then the application tier.
compose up -d --remove-orphans postgres keycloak opa git-mcp-server litellm
compose up -d --remove-orphans --wait --wait-timeout 600 \
  embabel-agent-runtime embabel-agent-service ai-control-service dashboard caddy
if [ "${ENABLE_MONITORING:-true}" = "true" ]; then
  compose --profile monitoring up -d prometheus alertmanager node-exporter
fi

"$DIR/scripts/smoke-test.sh" compose

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $TAG" >> "$HISTORY"
echo "deploy: $TAG is live (history: $HISTORY)"
