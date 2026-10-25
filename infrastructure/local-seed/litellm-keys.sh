#!/bin/sh
# Seeds the LiteLLM virtual keys of the local stack (idempotent; existing keys are reconciled).
#  1. embabel-agent-runtime (LITELLM_VIRTUAL_KEY): model calls of the agent runtime, including the
#     agent model aliases customer-chat / agent-chat from infrastructure/litellm/config.yaml.
#     It is also granted the local git MCP server (object_permission.mcp_servers: openjcockpit_git,
#     registered under mcp_servers in config.yaml) for the agents' git tools; its llm_api key type already
#     covers the MCP data plane (/mcp, /sse, /mcp-rest/tools/*) but no MCP management routes.
#  2. openjcockpit-a2a (LITELLM_A2A_KEY): the ONE A2A client key for the LiteLLM provider. Used by
#     embabel-agent-service's A2A provider client (agent catalog, agent cards, workflow A2A agents) and by local A2A callers
#     (scripts/a2a-roundtrip.sh). LiteLLM v1.101.2 shows a non-admin key only agents granted in
#     object_permission.agents (agent_id = sha256(agent_name) for config agents); allowed_routes limits
#     it to the registry listing and the A2A data plane (agent_inference_routes: /a2a/{agent_id},
#     message/send|stream, agent card) — no LLM or management routes.
#  3. ai-control-service (LITELLM_AI_CONTROL_KEY): git-workspace jobs clone, branch, write, commit and push
#     in a job workspace of the git MCP server. Only the MCP REST bridge routes and only the git MCP server
#     (object_permission.mcp_servers); no models, no agents. The git credentials never pass this key:
#     the git MCP server pulls them from ai-control itself.
#  4. embabel-agent-service-mcp (LITELLM_AGENT_SERVICE_KEY): checkpoint commits of the workflow engine. Like
#     ai-control's key: only the MCP REST bridge routes and only the git MCP server; no models, no agents.
#  Keys of earlier setups are deleted first (embabel-agent-catalog, openjcockpit-a2a-client, and the model key's
#  former alias embabel-agent-service, which is then re-created as embabel-agent-runtime with the same value).
set -e
trap 'rm -f /tmp/litellm-key-resp.json' EXIT

LITELLM_URL="${LITELLM_URL:-http://litellm:4000}"
LITELLM_KEY_ALIAS="${LITELLM_KEY_ALIAS:-embabel-agent-runtime}"
LITELLM_AGENT_SERVICE_KEY_ALIAS="${LITELLM_AGENT_SERVICE_KEY_ALIAS:-embabel-agent-service-mcp}"
LITELLM_A2A_KEY_ALIAS="${LITELLM_A2A_KEY_ALIAS:-openjcockpit-a2a}"
LITELLM_MCP_SERVERS="${LITELLM_MCP_SERVERS:-openjcockpit_git}"
LITELLM_AI_CONTROL_KEY_ALIAS="${LITELLM_AI_CONTROL_KEY_ALIAS:-ai-control-service}"
LITELLM_A2A_AGENTS="${LITELLM_A2A_AGENTS:-business-analyst solution-architect software-developer qa-engineer}"
LITELLM_LEGACY_KEY_ALIASES="${LITELLM_LEGACY_KEY_ALIASES:-embabel-agent-catalog openjcockpit-a2a-client embabel-agent-service}"

if [ -z "${LITELLM_MASTER_KEY}" ] || [ -z "${LITELLM_VIRTUAL_KEY}" ] || [ -z "${LITELLM_A2A_KEY}" ] \
   || [ -z "${LITELLM_AI_CONTROL_KEY}" ] || [ -z "${LITELLM_AGENT_SERVICE_KEY}" ]; then
  echo "local-seed[litellm]: LITELLM_MASTER_KEY / LITELLM_VIRTUAL_KEY / LITELLM_A2A_KEY / LITELLM_AI_CONTROL_KEY /"
  echo "                     LITELLM_AGENT_SERVICE_KEY must be set"
  exit 1
fi

# key_exists <alias>: 0 when a key with this alias exists.
key_exists() {
  status=$(curl -s -o /tmp/litellm-key-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" \
    "${LITELLM_URL}/key/list?key_alias=$1")
  if [ "${status}" != "200" ]; then
    echo "local-seed[litellm]: GET ${LITELLM_URL}/key/list failed with status ${status}"
    exit 1
  fi
  grep -q '"keys"[[:space:]]*:[[:space:]]*\[[^]]' /tmp/litellm-key-resp.json
}

# post <path> <json>: POST as master key, fail on non-2xx.
post() {
  status=$(curl -s -o /tmp/litellm-key-resp.json -w '%{http_code}' -X POST \
    -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" -H "Content-Type: application/json" \
    -d "$2" "${LITELLM_URL}$1")
  case "${status}" in
    2*) ;;
    *) echo "local-seed[litellm]: POST ${LITELLM_URL}$1 failed with status ${status}"; exit 1 ;;
  esac
}

# seed_key <alias> <create-json> <update-json>
seed_key() {
  if key_exists "$1"; then
    post /key/update "$3"
    echo "local-seed[litellm]: key '$1' updated"
  else
    post /key/generate "$2"
    echo "local-seed[litellm]: key '$1' created"
  fi
}

# Obsolete aliases first, so a key whose alias changed can be re-created with the same value below.
for alias in ${LITELLM_LEGACY_KEY_ALIASES}; do
  if key_exists "${alias}"; then
    post /key/delete "$(printf '{"key_aliases":["%s"]}' "${alias}")"
    echo "local-seed[litellm]: obsolete key '${alias}' deleted"
  fi
done

models=$(printf '"%s","customer-chat","agent-chat"' "${LITELLM_KEY_MODEL}")
mcp_servers=""
for server in ${LITELLM_MCP_SERVERS}; do
  mcp_servers="${mcp_servers:+${mcp_servers},}\"${server}\""
done
mcp_grant=$(printf '"object_permission":{"mcp_servers":[%s]}' "${mcp_servers}")
seed_key "${LITELLM_KEY_ALIAS}" \
  "$(printf '{"key":"%s","key_alias":"%s","key_type":"llm_api","models":[%s],%s,"user_id":"%s"}' \
    "${LITELLM_VIRTUAL_KEY}" "${LITELLM_KEY_ALIAS}" "${models}" "${mcp_grant}" "${LITELLM_KEY_ALIAS}")" \
  "$(printf '{"key":"%s","models":[%s],%s}' "${LITELLM_VIRTUAL_KEY}" "${models}" "${mcp_grant}")"

agent_ids=""
for name in ${LITELLM_A2A_AGENTS}; do
  id=$(printf '%s' "${name}" | sha256sum | cut -d' ' -f1)
  agent_ids="${agent_ids:+${agent_ids},}\"${id}\""
done
grants=$(printf '"allowed_routes":["/v1/agents","agent_inference_routes"],"object_permission":{"agents":[%s]}' "${agent_ids}")
seed_key "${LITELLM_A2A_KEY_ALIAS}" \
  "$(printf '{"key":"%s","key_alias":"%s","models":[],%s,"user_id":"%s"}' \
    "${LITELLM_A2A_KEY}" "${LITELLM_A2A_KEY_ALIAS}" "${grants}" "${LITELLM_A2A_KEY_ALIAS}")" \
  "$(printf '{"key":"%s",%s}' "${LITELLM_A2A_KEY}" "${grants}")"

mcp_only=$(printf '"allowed_routes":["/mcp-rest/tools/call","/mcp-rest/tools/list"],%s' "${mcp_grant}")
seed_key "${LITELLM_AI_CONTROL_KEY_ALIAS}" \
  "$(printf '{"key":"%s","key_alias":"%s","models":[],%s,"user_id":"%s"}' \
    "${LITELLM_AI_CONTROL_KEY}" "${LITELLM_AI_CONTROL_KEY_ALIAS}" "${mcp_only}" "${LITELLM_AI_CONTROL_KEY_ALIAS}")" \
  "$(printf '{"key":"%s",%s}' "${LITELLM_AI_CONTROL_KEY}" "${mcp_only}")"

seed_key "${LITELLM_AGENT_SERVICE_KEY_ALIAS}" \
  "$(printf '{"key":"%s","key_alias":"%s","models":[],%s,"user_id":"%s"}' \
    "${LITELLM_AGENT_SERVICE_KEY}" "${LITELLM_AGENT_SERVICE_KEY_ALIAS}" "${mcp_only}" "${LITELLM_AGENT_SERVICE_KEY_ALIAS}")" \
  "$(printf '{"key":"%s",%s}' "${LITELLM_AGENT_SERVICE_KEY}" "${mcp_only}")"
