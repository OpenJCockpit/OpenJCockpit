#!/usr/bin/env bash
# Validates a production environment file before anything is deployed:
#   deploy/scripts/validate-env.sh [deploy/compose/.env]
# Fails (exit 1) on unset or placeholder values, the repository's well-known local secrets, weak or
# malformed secrets, mutable image tags and loose file permissions. Prints variable NAMES only, never values.
# The same placeholder rules are enforced again at service start by ProductionConfigGuard.
set -euo pipefail
(( BASH_VERSINFO[0] >= 4 )) || { echo "validate-env: bash >= 4 required (Linux hosts have it; macOS: brew install bash)"; exit 2; }

ENV_FILE="${1:-$(dirname "$0")/../compose/.env}"
[ -f "$ENV_FILE" ] || { echo "validate-env: $ENV_FILE not found (copy deploy/compose/.env.prod.example)"; exit 1; }

errors=0
fail() { echo "validate-env: ERROR $*"; errors=$((errors + 1)); }

perm=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")
case "$perm" in 600|400) ;; *) fail "$ENV_FILE has mode $perm; run: chmod 600 $ENV_FILE" ;; esac

# Load without executing anything: KEY=VALUE lines only.
declare -A env
while IFS= read -r line || [ -n "$line" ]; do
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  # Inline comments need whitespace before '#', as in docker compose env files ('#' inside a secret stays).
  line="$(sed -E 's/[[:space:]]+#.*$//' <<< "$line")"
  [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
  value="${BASH_REMATCH[2]}"; value="${value#\"}"; value="${value%\"}"
  env["${BASH_REMATCH[1]}"]="$value"
done < "$ENV_FILE"

dev_placeholder='(local|dev-only|change-?me|change-this|welkom|test-only|example|placeholder|__required__)'
dev_exact='^(admin|keycloak|0123456789abcdef|a2a5e7c1ce5a17ed)$'

required=(OPENJCOCKPIT_REGISTRY OPENJCOCKPIT_IMAGE_TAG OPENJCOCKPIT_HOST KEYCLOAK_HOST ACME_EMAIL AGENT_WORKFLOWS_TENANT
  OLLAMA_BASE_URL OLLAMA_MODEL)
secrets=(POSTGRES_SUPERUSER_PASSWORD KEYCLOAK_DB_PASSWORD OPENJCOCKPIT_DB_PASSWORD EMBABEL_DB_PASSWORD LITELLM_DB_PASSWORD
  KC_BOOTSTRAP_ADMIN_PASSWORD A2A_CLIENT_SECRET GIT_MCP_CLIENT_SECRET AI_CONTROL_CLIENT_SECRET AGENT_SERVICE_CLIENT_SECRET
  AGENT_RUNTIME_CLIENT_SECRET LITELLM_MASTER_KEY LITELLM_SALT_KEY LITELLM_VIRTUAL_KEY LITELLM_A2A_KEY LITELLM_AI_CONTROL_KEY
  LITELLM_AGENT_SERVICE_KEY OPENAI_API_KEY ENCRYPTION_SECRET A2A_SERVICE_ENCRYPTION_SECRET SPEC_QUEUE_RUNNER_TOKEN_SECRET)

for k in "${required[@]}" POSTGRES_SUPERUSER KC_BOOTSTRAP_ADMIN_USERNAME; do
  v="${env[$k]:-}"
  [ -n "$v" ] || { fail "$k is not set"; continue; }
  [[ "${v,,}" == "__required__" ]] && fail "$k is still a placeholder"
done

for k in "${secrets[@]}"; do
  v="${env[$k]:-}"
  if [ -z "$v" ]; then fail "$k is not set"; continue; fi
  [ "${#v}" -ge 16 ] || fail "$k is shorter than 16 characters"
  if [[ "${v,,}" =~ $dev_placeholder ]] || [[ "${v,,}" =~ $dev_exact ]]; then
    fail "$k is a development placeholder"
  fi
done

# Every secret must be distinct: a shared value turns one leak into many.
declare -A seen
for k in "${secrets[@]}"; do
  v="${env[$k]:-}"; [ -n "$v" ] || continue
  if [ -n "${seen[$v]:-}" ]; then fail "$k reuses the value of ${seen[$v]}"; else seen[$v]="$k"; fi
done

for k in ENCRYPTION_SALT A2A_SERVICE_ENCRYPTION_SALT; do
  v="${env[$k]:-}"
  [[ "$v" =~ ^[0-9a-fA-F]{16}$ ]] || fail "$k must be exactly 16 hex characters"
  [[ "${v,,}" =~ $dev_exact ]] && fail "$k is a development placeholder"
done
[ "${#env[ENCRYPTION_SECRET]}" -ge 32 ] 2>/dev/null || fail "ENCRYPTION_SECRET must be at least 32 characters"
[ "${#env[SPEC_QUEUE_RUNNER_TOKEN_SECRET]}" -ge 32 ] 2>/dev/null || fail "SPEC_QUEUE_RUNNER_TOKEN_SECRET must be at least 32 characters"
for k in LITELLM_MASTER_KEY LITELLM_VIRTUAL_KEY LITELLM_A2A_KEY LITELLM_AI_CONTROL_KEY LITELLM_AGENT_SERVICE_KEY; do
  [[ "${env[$k]:-}" == sk-* ]] || fail "$k must start with sk-"
done

tag="${env[OPENJCOCKPIT_IMAGE_TAG]:-}"
[[ "$tag" == "latest" || "$tag" == "local" || "$tag" == "dev" ]] && fail "OPENJCOCKPIT_IMAGE_TAG must be an immutable release tag"
for k in OPENJCOCKPIT_HOST KEYCLOAK_HOST; do
  [[ "${env[$k]:-}" =~ ^(localhost|127\.|0\.0\.0\.0)|://  ]] && fail "$k must be a public host name without scheme"
done
[ "${env[OPENJCOCKPIT_HOST]:-a}" != "${env[KEYCLOAK_HOST]:-b}" ] || fail "OPENJCOCKPIT_HOST and KEYCLOAK_HOST must differ"

if [ "$errors" -gt 0 ]; then
  echo "validate-env: $errors problem(s) in $ENV_FILE"
  exit 1
fi
echo "validate-env: $ENV_FILE OK"
