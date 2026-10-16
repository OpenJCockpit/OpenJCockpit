#!/usr/bin/env bash
# Live A2A roundtrip against the local docker-compose stack:
#   client -> LiteLLM A2A Gateway (:4100) -> embabel-agent-service -> LiteLLM model gateway -> Ollama
# Uses the credentials seeded by `docker compose up` (local-seed): Keycloak client `openjcockpit-a2a`
# (client_credentials; tokens carry tenant_id=local) and the LiteLLM A2A key `openjcockpit-a2a`.
# Usage: ./scripts/a2a-roundtrip.sh [agent ...]   (default: the four seeded agents)
set -euo pipefail
LITELLM_URL="${LITELLM_URL:-http://localhost:4100}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
LITELLM_A2A_KEY="${LITELLM_A2A_KEY:-sk-openjcockpit-a2a-local}"
A2A_CLIENT_ID="${A2A_CLIENT_ID:-openjcockpit-a2a}"
A2A_CLIENT_SECRET="${A2A_CLIENT_SECRET:-openjcockpit-a2a-local-secret}"
agents=("$@")
[ ${#agents[@]} -eq 0 ] && agents=(business-analyst solution-architect software-developer qa-engineer)

jwt=$(curl -sS --fail-with-body -X POST "$KEYCLOAK_URL/realms/openjcockpit/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id="$A2A_CLIENT_ID" --data-urlencode client_secret="$A2A_CLIENT_SECRET" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')

failed=0
for agent in "${agents[@]}"; do
  body=$(printf '{"jsonrpc":"2.0","id":"rt-%s","method":"message/send","params":{"message":{"kind":"message","role":"user","messageId":"rt-%s","parts":[{"kind":"text","text":"Introduce yourself in one sentence."}]}}}' "$agent" "$agent")
  if response=$(curl -sS --fail-with-body --max-time 900 -X POST "$LITELLM_URL/a2a/$agent" \
      -H "Content-Type: application/json" \
      -H "x-litellm-api-key: Bearer $LITELLM_A2A_KEY" \
      -H "x-a2a-$agent-authorization: Bearer $jwt" \
      -H "traceparent: 00-$(openssl rand -hex 16)-$(openssl rand -hex 8)-01" \
      -d "$body") && echo "$response" | python3 -c '
import json,sys
r=json.load(sys.stdin)
assert "error" not in r, r.get("error")
res=r["result"]
msg=res if res.get("kind")=="message" else (res.get("message") or res)
parts=msg.get("parts") or [p for a in res.get("artifacts",[]) for p in a.get("parts",[])]
text=" ".join(p.get("text","") for p in parts)
print("OK   %-20s %s" % (sys.argv[1], text[:100].replace("\n"," ")))' "$agent"; then
    :
  else
    echo "FAIL $agent: ${response:-no response}" | cut -c1-300
    failed=1
  fi
done
exit $failed
