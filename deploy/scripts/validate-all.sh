#!/usr/bin/env bash
# Runs every offline check of the deployment profiles locally (the same checks as infra-ci.yml):
#   deploy/scripts/validate-all.sh
# Needs Docker and kubectl; nothing is deployed and no real secret is used.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
OPA=openpolicyagent/opa:0.70.0@sha256:2b1c4b231f6dfa6a2baea4af4109203b10cd4b65e836d72f03bfe5d8dfce7095
step() { echo; echo "== $*"; }

step "OPA policies"
docker run --rm -v "$ROOT/infrastructure/opa/policies:/p:ro" "$OPA" check --strict /p
docker run --rm -v "$ROOT/infrastructure/opa/policies:/p:ro" "$OPA" test /p

step "Generated files up to date"
python3 deploy/scripts/render-prod-realm.py --check
deploy/scripts/render-prometheusrule.sh --check

step "Shellcheck"
docker run --rm -v "$ROOT:/w:ro" -w /w koalaman/shellcheck:v0.10.0 -S warning deploy/scripts/*.sh deploy/compose/postgres/*.sh

step "Environment validation (template rejected, generated env accepted)"
cp deploy/compose/.env.prod.example "$TMP/template.env"; chmod 600 "$TMP/template.env"
if docker run --rm -v "$ROOT/deploy/scripts:/s:ro" -v "$TMP:/e" bash:5.2 bash /s/validate-env.sh /e/template.env >/dev/null; then
  echo "template accepted"; exit 1
fi
python3 - "$TMP" <<'PY'
import re, secrets, sys
fixed = {"OPENJCOCKPIT_IMAGE_TAG": "v0.0.0-check", "OPENJCOCKPIT_HOST": "cockpit.check.test", "KEYCLOAK_HOST": "auth.check.test",
         "ACME_EMAIL": "ops@check.test", "AGENT_WORKFLOWS_TENANT": "check", "OLLAMA_BASE_URL": "https://ollama.check.test",
         "OLLAMA_MODEL": "qwen3.6:27b", "POSTGRES_SUPERUSER": "pgsuper", "KC_BOOTSTRAP_ADMIN_USERNAME": "bootstrap-ops",
         "ENCRYPTION_SALT": secrets.token_hex(8), "A2A_SERVICE_ENCRYPTION_SALT": secrets.token_hex(8)}
out = []
for line in open(sys.argv[1] + "/template.env"):
    m = re.match(r"^([A-Z0-9_]+)=__REQUIRED__", line)
    if m:
        k = m.group(1)
        v = fixed.get(k) or (("sk-" if k.startswith("LITELLM_") and k.endswith("_KEY") and k != "LITELLM_SALT_KEY" else "") + secrets.token_urlsafe(30))
        line = f"{k}={v}\n"
    out.append(line)
open(sys.argv[1] + "/check.env", "w").write("".join(out))
PY
chmod 600 "$TMP/check.env"
docker run --rm -v "$ROOT/deploy/scripts:/s:ro" -v "$TMP:/e" bash:5.2 bash /s/validate-env.sh /e/check.env

step "Production compose profile"
docker compose -f deploy/compose/docker-compose.prod.yml --env-file "$TMP/check.env" --profile monitoring config -q
! docker compose -f deploy/compose/docker-compose.prod.yml --env-file "$TMP/check.env" config \
    | grep -nE 'docker\.sock|privileged: true|Welkom|-local-secret|sk-openjcockpit'
echo "no development values, Docker socket or privileged containers"

step "Kubernetes profile"
kubectl kustomize --load-restrictor LoadRestrictionsNone deploy/k8s/overlays/production > "$TMP/k8s.yaml"
docker run --rm -v "$TMP/k8s.yaml:/m.yaml:ro" ghcr.io/yannh/kubeconform:v0.7.0 -strict -summary -ignore-missing-schemas -kubernetes-version 1.31.0 /m.yaml
! grep -nE 'hostPath|docker\.sock|privileged: true|hostNetwork: true' "$TMP/k8s.yaml"
echo "no host access"

step "Alert rules"
docker run --rm --entrypoint promtool -v "$ROOT/deploy/monitoring:/m:ro" \
  prom/prometheus:v3.5.0@sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996 check rules /m/alerts.rules.yml

step "Backup / restore drill"
deploy/scripts/backup-restore-drill.sh

echo; echo "validate-all: OK"
