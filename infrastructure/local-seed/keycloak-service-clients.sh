#!/bin/sh
# Part of the local seed (seed-local.sh). Idempotently seeds the confidential Keycloak service clients
# of the local stack (client_credentials only: no browser login, no password grant). Local development only.
#  - openjcockpit-a2a       A2A callers (the workflow engine, scripts/a2a-roundtrip.sh) -> embabel A2A agents.
#                           Tokens carry a hard-coded tenant claim (A2A_TENANT_CLAIM=A2A_TENANT, default
#                           tenant_id=local) that selects the agent_tenant_bindings rows of the local seed.
#  - openjcockpit-git-mcp   git-mcp-server -> ai-control: pulls the git credentials of an active
#                           git-workspace job workspace (GET /internal/git-workspaces/{key}/credentials).
#  - openjcockpit-litellm-git  LiteLLM -> git-mcp-server: LiteLLM's own token on the upstream MCP connection
#                           (mcp_servers.openjcockpit_git, oauth2_flow client_credentials). Seeded only when
#                           LITELLM_GIT_MCP_CLIENT_SECRET is set.
#  - openjcockpit-ai-control ai-control -> embabel: git-workspace jobs start their workflow with this
#                           identity; only it may bind a run to a job's git workspace.
#  - openjcockpit-agent-service  embabel-agent-service -> embabel-agent-runtime: runs agents and definition
#                           drafts (/internal/v1/**); the runtime refuses every other caller.
#  - openjcockpit-agent-runtime  embabel-agent-runtime -> embabel-agent-service: reads tenant bindings, active
#                           workflow runs and tool-call policy decisions (/internal/agent-runtime/v1/**).
#  - openjcontroltower-service  OpenJControlTower -> ai-control-service: enqueues dispatched specs
#                           (POST /api/projects/{id}/spec-queue/items). Its service account holds the
#                           openjcockpit-operator realm role, which that endpoint requires. Seeded only when
#                           CONTROLTOWER_CLIENT_SECRET is set (ControlTower is a separate, optional deployment).
# Also adds the audience `openjcontroltower` to the access tokens of the dashboard client `openjcockpit`, so the
# ControlTower backend (which validates `aud`) accepts the dashboard user's token, and the audience
# `openjcockpit-git-mcp` (GIT_MCP_AUDIENCE) to the tokens of the git-mcp-server callers openjcockpit-litellm-git and
# openjcockpit-agent-service, which git-mcp-server requires with KEYCLOAK_ENABLED=true (its KEYCLOAK_ALLOWED_AZP
# lists the same two clients).
# Re-running reconciles the secrets and adds the tenant and audience mappers when missing.
set -e
trap 'rm -f /tmp/kc-seed-resp.json' EXIT

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-openjcockpit}"
A2A_CLIENT_ID="${A2A_CLIENT_ID:-openjcockpit-a2a}"
A2A_TENANT_CLAIM="${A2A_TENANT_CLAIM:-tenant_id}"
A2A_TENANT="${A2A_TENANT:-local}"
GIT_MCP_CLIENT_ID="${GIT_MCP_CLIENT_ID:-openjcockpit-git-mcp}"
GIT_MCP_AUDIENCE="${GIT_MCP_AUDIENCE:-openjcockpit-git-mcp}"
LITELLM_GIT_MCP_CLIENT_ID="${LITELLM_GIT_MCP_CLIENT_ID:-openjcockpit-litellm-git}"
AI_CONTROL_CLIENT_ID="${AI_CONTROL_CLIENT_ID:-openjcockpit-ai-control}"
AGENT_SERVICE_CLIENT_ID="${AGENT_SERVICE_CLIENT_ID:-openjcockpit-agent-service}"
AGENT_RUNTIME_CLIENT_ID="${AGENT_RUNTIME_CLIENT_ID:-openjcockpit-agent-runtime}"
DASHBOARD_CLIENT_ID="${DASHBOARD_CLIENT_ID:-openjcockpit}"
CONTROLTOWER_AUDIENCE="${CONTROLTOWER_AUDIENCE:-openjcontroltower}"
CONTROLTOWER_CLIENT_ID="${CONTROLTOWER_CLIENT_ID:-openjcontroltower-service}"
CONTROLTOWER_SERVICE_ROLE="${CONTROLTOWER_SERVICE_ROLE:-openjcockpit-operator}"

if [ -z "$KC_BOOTSTRAP_ADMIN_USERNAME" ] || [ -z "$KC_BOOTSTRAP_ADMIN_PASSWORD" ] || [ -z "$A2A_CLIENT_SECRET" ] \
   || [ -z "$GIT_MCP_CLIENT_SECRET" ] || [ -z "$AI_CONTROL_CLIENT_SECRET" ] \
   || [ -z "$AGENT_SERVICE_CLIENT_SECRET" ] || [ -z "$AGENT_RUNTIME_CLIENT_SECRET" ]; then
  echo "local-seed[keycloak]: KC_BOOTSTRAP_ADMIN_USERNAME / KC_BOOTSTRAP_ADMIN_PASSWORD / A2A_CLIENT_SECRET /"
  echo "                      GIT_MCP_CLIENT_SECRET / AI_CONTROL_CLIENT_SECRET /"
  echo "                      AGENT_SERVICE_CLIENT_SECRET / AGENT_RUNTIME_CLIENT_SECRET must be set"
  exit 1
fi

TOKEN=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${KC_BOOTSTRAP_ADMIN_USERNAME}" \
  --data-urlencode "password=${KC_BOOTSTRAP_ADMIN_PASSWORD}" \
  "${KC_URL}/realms/master/protocol/openid-connect/token" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "local-seed[keycloak]: failed to obtain admin token"
  exit 1
fi

# client_uuid <clientId>: prints the internal id of the client, empty when absent.
client_uuid() {
  status=$(curl -s -G -o /tmp/kc-seed-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    --data-urlencode "clientId=$1" \
    "${KC_URL}/admin/realms/${KC_REALM}/clients")
  if [ "$status" != "200" ]; then
    echo "local-seed[keycloak]: GET clients -> ${status}" >&2
    exit 1
  fi
  sed -n 's/^\[{"id":"\([^"]*\)".*/\1/p' /tmp/kc-seed-resp.json | head -n1
}

# seed_client <clientId> <secret> <display name>: creates or reconciles a client_credentials-only client.
seed_client() {
  client=$(printf '{"clientId":"%s","name":"%s","enabled":true,"protocol":"openid-connect","publicClient":false,"clientAuthenticatorType":"client-secret","secret":"%s","serviceAccountsEnabled":true,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false}' \
    "$1" "$3" "$2")
  id=$(client_uuid "$1")
  if [ -z "$id" ]; then
    status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' -X POST \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -d "$client" "${KC_URL}/admin/realms/${KC_REALM}/clients")
    if [ "$status" != "201" ]; then
      echo "local-seed[keycloak]: POST client $1 -> ${status}"
      exit 1
    fi
    echo "local-seed[keycloak]: client '$1' created"
  else
    status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' -X PUT \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -d "$client" "${KC_URL}/admin/realms/${KC_REALM}/clients/${id}")
    if [ "$status" != "204" ]; then
      echo "local-seed[keycloak]: PUT client $1 -> ${status}"
      exit 1
    fi
    echo "local-seed[keycloak]: client '$1' updated"
  fi
}

seed_client "$A2A_CLIENT_ID" "$A2A_CLIENT_SECRET" "A2A callers (local)"
seed_client "$GIT_MCP_CLIENT_ID" "$GIT_MCP_CLIENT_SECRET" "git-mcp-server (local)"
seed_client "$AI_CONTROL_CLIENT_ID" "$AI_CONTROL_CLIENT_SECRET" "ai-control-service jobs (local)"
seed_client "$AGENT_SERVICE_CLIENT_ID" "$AGENT_SERVICE_CLIENT_SECRET" "embabel-agent-service -> runtime (local)"
seed_client "$AGENT_RUNTIME_CLIENT_ID" "$AGENT_RUNTIME_CLIENT_SECRET" "embabel-agent-runtime -> service (local)"

# Tenant claim on the A2A client's tokens only.
a2a_id=$(client_uuid "$A2A_CLIENT_ID")
MAPPER=$(printf '{"name":"%s","protocol":"openid-connect","protocolMapper":"oidc-hardcoded-claim-mapper","config":{"claim.name":"%s","claim.value":"%s","jsonType.label":"String","access.token.claim":"true","id.token.claim":"false","userinfo.token.claim":"false","introspection.token.claim":"true"}}' \
  "$A2A_TENANT_CLAIM" "$A2A_TENANT_CLAIM" "$A2A_TENANT")
status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  "${KC_URL}/admin/realms/${KC_REALM}/clients/${a2a_id}/protocol-mappers/models")
if [ "$status" != "200" ]; then
  echo "local-seed[keycloak]: GET protocol mappers -> ${status}"
  exit 1
fi
if grep -q "\"name\":\"${A2A_TENANT_CLAIM}\"" /tmp/kc-seed-resp.json; then
  echo "local-seed[keycloak]: tenant mapper present"
else
  status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' -X POST \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -d "$MAPPER" "${KC_URL}/admin/realms/${KC_REALM}/clients/${a2a_id}/protocol-mappers/models")
  if [ "$status" != "201" ]; then
    echo "local-seed[keycloak]: POST tenant mapper -> ${status}"
    exit 1
  fi
  echo "local-seed[keycloak]: tenant mapper ${A2A_TENANT_CLAIM}=${A2A_TENANT} added"
fi

# ensure_audience_mapper <clientId> <audience>: adds <audience> to the aud claim of the client's access tokens
# (mapper "<audience>-audience"), unless that mapper is already present.
ensure_audience_mapper() {
  cid=$(client_uuid "$1")
  if [ -z "$cid" ]; then
    echo "local-seed[keycloak]: client '$1' not found"
    exit 1
  fi
  mapper_name="$2-audience"
  mapper=$(printf '{"name":"%s","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.custom.audience":"%s","access.token.claim":"true","id.token.claim":"false","introspection.token.claim":"true"}}' \
    "$mapper_name" "$2")
  status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    "${KC_URL}/admin/realms/${KC_REALM}/clients/${cid}/protocol-mappers/models")
  if [ "$status" != "200" ]; then
    echo "local-seed[keycloak]: GET protocol mappers of $1 -> ${status}"
    exit 1
  fi
  if grep -q "\"name\":\"${mapper_name}\"" /tmp/kc-seed-resp.json; then
    echo "local-seed[keycloak]: audience $2 already on $1 access tokens"
  else
    status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' -X POST \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -d "$mapper" "${KC_URL}/admin/realms/${KC_REALM}/clients/${cid}/protocol-mappers/models")
    if [ "$status" != "201" ]; then
      echo "local-seed[keycloak]: POST audience mapper $2 on $1 -> ${status}"
      exit 1
    fi
    echo "local-seed[keycloak]: audience $2 added to $1 access tokens"
  fi
}

# Dashboard user tokens are accepted by the ControlTower backend (which validates aud).
ensure_audience_mapper "$DASHBOARD_CLIENT_ID" "$CONTROLTOWER_AUDIENCE"

# Callers of git-mcp-server (incoming MCP authentication): LiteLLM's upstream identity and embabel-agent-service.
if [ -n "$LITELLM_GIT_MCP_CLIENT_SECRET" ]; then
  seed_client "$LITELLM_GIT_MCP_CLIENT_ID" "$LITELLM_GIT_MCP_CLIENT_SECRET" "LiteLLM -> git-mcp-server"
  ensure_audience_mapper "$LITELLM_GIT_MCP_CLIENT_ID" "$GIT_MCP_AUDIENCE"
else
  echo "local-seed[keycloak]: LITELLM_GIT_MCP_CLIENT_SECRET not set, skipping client '${LITELLM_GIT_MCP_CLIENT_ID}'"
fi
ensure_audience_mapper "$AGENT_SERVICE_CLIENT_ID" "$GIT_MCP_AUDIENCE"

# ControlTower dispatch identity: a client_credentials client whose service account may enqueue specs.
if [ -z "$CONTROLTOWER_CLIENT_SECRET" ]; then
  echo "local-seed[keycloak]: CONTROLTOWER_CLIENT_SECRET not set, skipping client '${CONTROLTOWER_CLIENT_ID}'"
  exit 0
fi
seed_client "$CONTROLTOWER_CLIENT_ID" "$CONTROLTOWER_CLIENT_SECRET" "OpenJControlTower dispatch (local)"
ct_id=$(client_uuid "$CONTROLTOWER_CLIENT_ID")
status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  "${KC_URL}/admin/realms/${KC_REALM}/clients/${ct_id}/service-account-user")
sa_id=$(sed -n 's/^{"id":"\([^"]*\)".*/\1/p' /tmp/kc-seed-resp.json | head -n1)
if [ "$status" != "200" ] || [ -z "$sa_id" ]; then
  echo "local-seed[keycloak]: GET service-account-user of ${CONTROLTOWER_CLIENT_ID} -> ${status}"
  exit 1
fi
status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  "${KC_URL}/admin/realms/${KC_REALM}/roles/${CONTROLTOWER_SERVICE_ROLE}")
role_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-seed-resp.json | head -n1)
if [ "$status" != "200" ] || [ -z "$role_id" ]; then
  echo "local-seed[keycloak]: role '${CONTROLTOWER_SERVICE_ROLE}' not found (GET -> ${status}); run seed-realm-roles.sh first"
  exit 1
fi
status=$(curl -s -o /tmp/kc-seed-resp.json -w '%{http_code}' -X POST \
  -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
  -d "$(printf '[{"id":"%s","name":"%s"}]' "$role_id" "$CONTROLTOWER_SERVICE_ROLE")" \
  "${KC_URL}/admin/realms/${KC_REALM}/users/${sa_id}/role-mappings/realm")
if [ "$status" != "204" ]; then
  echo "local-seed[keycloak]: POST role-mapping ${CONTROLTOWER_SERVICE_ROLE} for ${CONTROLTOWER_CLIENT_ID} -> ${status}"
  exit 1
fi
echo "local-seed[keycloak]: ${CONTROLTOWER_CLIENT_ID} service account holds ${CONTROLTOWER_SERVICE_ROLE}"
