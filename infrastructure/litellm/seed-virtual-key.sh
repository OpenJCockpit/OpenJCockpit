#!/bin/sh
# Seed the LiteLLM virtual API key for the local agent service (idempotent).
set -e
trap 'rm -f /tmp/litellm-key-resp.json' EXIT

LITELLM_URL="${LITELLM_URL:-http://litellm:4000}"
LITELLM_KEY_ALIAS="${LITELLM_KEY_ALIAS:-embabel-agent-service}"

if [ -z "${LITELLM_MASTER_KEY}" ] || [ -z "${LITELLM_VIRTUAL_KEY}" ]; then
  echo "litellm-seed-key: LITELLM_MASTER_KEY / LITELLM_VIRTUAL_KEY must be set"
  exit 1
fi

# Existence probe: /key/list with an exact alias match. Deliberately NOT
# /key/info?key=<secret>, which would leak the secret into a URL (BR-8).
status=$(curl -s -o /tmp/litellm-key-resp.json -w '%{http_code}' \
  -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" \
  "${LITELLM_URL}/key/list?key_alias=${LITELLM_KEY_ALIAS}")

if [ "${status}" != "200" ]; then
  echo "litellm-seed-key: GET ${LITELLM_URL}/key/list failed with status ${status}"
  exit 1
fi

# A non-empty keys array means a key with this alias already exists.
if grep -q '"keys"[[:space:]]*:[[:space:]]*\[[^]]' /tmp/litellm-key-resp.json; then
  echo "litellm-seed-key: key '${LITELLM_KEY_ALIAS}' already present"
  exit 0
fi

# Generate the key. Body built with printf from env vars -- never hardcoded.
# key_type llm_api scopes the key away from admin/management endpoints (AC-8a).
body=$(printf '{"key":"%s","key_alias":"%s","key_type":"llm_api","models":["%s"],"user_id":"%s"}' \
  "${LITELLM_VIRTUAL_KEY}" "${LITELLM_KEY_ALIAS}" "${LITELLM_KEY_MODEL}" "${LITELLM_KEY_ALIAS}")

status=$(curl -s -o /tmp/litellm-key-resp.json -w '%{http_code}' \
  -X POST \
  -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" \
  -H "Content-Type: application/json" \
  -d "${body}" \
  "${LITELLM_URL}/key/generate")

case "${status}" in
  2*)
    echo "litellm-seed-key: key '${LITELLM_KEY_ALIAS}' ready"
    ;;
  *)
    echo "litellm-seed-key: POST ${LITELLM_URL}/key/generate failed with status ${status}"
    exit 1
    ;;
esac
