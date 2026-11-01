#!/usr/bin/env bash
# Post-deployment smoke test (also the "deployment health" part of the release gate):
#   deploy/scripts/smoke-test.sh compose                 # VM profile, run on the host
#   deploy/scripts/smoke-test.sh k8s [namespace]         # Kubernetes, uses kubectl
# Checks: every service reports readiness UP on its management port; the public API rejects anonymous
# calls (401, deny by default); actuator is not reachable through the public entry point; OPA allows a
# valid workflow start and denies a forbidden tool operation and an unidentified request.
# shellcheck disable=SC2319  # result() deliberately reads the status of the preceding test
set -euo pipefail

MODE="${1:-compose}"
NS="${2:-openjcockpit}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
CURL_IMAGE="curlimages/curl:8.21.0@sha256:7c12af72ceb38b7432ab85e1a265cff6ae58e06f95539d539b654f2cfa64bb13"
failures=0

# Runs curl from inside the deployment's private network (nothing there is published).
incluster() {
  if [ "$MODE" = "compose" ]; then
    docker run --rm --network openjcockpit_backend "$CURL_IMAGE" "$@"
  else
    kubectl -n "$NS" run "smoke-$RANDOM" --rm -i --quiet --restart=Never --image="$CURL_IMAGE" \
      --labels=app.kubernetes.io/part-of=openjcockpit,openjcockpit.io/smoke=true \
      --overrides='{"spec":{"securityContext":{"runAsNonRoot":true,"runAsUser":100,"seccompProfile":{"type":"RuntimeDefault"}}}}' \
      -- curl "$@"
  fi
}

if [ "$MODE" = "compose" ]; then
  PUBLIC="https://$(grep -E '^OPENJCOCKPIT_HOST=' "$DIR/compose/.env" | cut -d= -f2-)"
else
  PUBLIC="https://$(kubectl -n "$NS" get ingress openjcockpit -o jsonpath='{.spec.rules[0].host}')"
fi

result() {  # $1 description, $2 exit status of the check
  if [ "$2" -eq 0 ]; then echo "smoke: PASS $1"; else echo "smoke: FAIL $1"; failures=$((failures + 1)); fi
}
status_of() { curl -s -o /dev/null -w '%{http_code}' "$1" || true; }
opa() {
  incluster -fsS -X POST -H 'Content-Type: application/json' -d "{\"input\":$1}" \
    http://opa:8181/v1/data/openjcockpit/workflow/decision
}

# Each check records its own result; keep going after a failure.
set +e
for svc in ai-control-service embabel-agent-service embabel-agent-runtime git-mcp-server; do
  incluster -fsS "http://$svc:9090/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'
  result "$svc readiness UP" $?
done

[ "$(status_of "$PUBLIC/api/projects")" = 401 ]; result "anonymous API call is rejected with 401" $?
[ "$(status_of "$PUBLIC/actuator/health")" = 404 ]; result "actuator is not exposed publicly" $?
curl -fsS -o /dev/null "$PUBLIC/"; result "dashboard is served over TLS" $?

opa '{"workflowId":"smoke","customerId":"smoke","action":"workflow.start"}' | grep -q '"allowed":true'
result "OPA allows a valid workflow start" $?
opa '{"workflowId":"smoke","customerId":"smoke","action":"mcp.tool.invoke","mcpToolName":"git_force_push"}' | grep -q '"allowed":false'
result "OPA denies a forbidden tool operation" $?
opa '{"action":"workflow.start"}' | grep -q '"allowed":false'
result "OPA denies an unidentified request" $?

if [ "$failures" -gt 0 ]; then echo "smoke: $failures check(s) failed"; exit 1; fi
echo "smoke: all checks passed"
