#!/usr/bin/env bash
# Reproducible, re-runnable Kubernetes deployment (also the rollback path: deploy the previous tag):
#   deploy/scripts/deploy-k8s.sh <overlay-dir> <release-tag> <env-file> [--dry-run]
# Example: deploy/scripts/deploy-k8s.sh deploy/k8s/overlays/production v1.4.2 ~/secure/openjcockpit-prod.env
# Steps: validate env -> render manifests (tag pinned) -> schema-safe server dry run -> secret from the env
# file -> apply -> bootstrap job -> rollout status -> smoke test -> record release on the namespace.
set -euo pipefail

OVERLAY="${1:?overlay dir}"; TAG="${2:?release tag}"; ENV_FILE="${3:?env file}"; DRY="${4:-}"
NS=openjcockpit
DIR="$(cd "$(dirname "$0")/.." && pwd)"
[[ "$TAG" =~ ^(latest|local|dev)$ ]] && { echo "deploy-k8s: '$TAG' is not an immutable release tag"; exit 1; }

"$DIR/scripts/validate-env.sh" "$ENV_FILE"

RENDERED="$(mktemp)"; trap 'rm -f "$RENDERED"' EXIT
kubectl kustomize --load-restrictor LoadRestrictionsNone "$OVERLAY" | sed "s/:RELEASE_TAG\$/:$TAG/" > "$RENDERED"
! grep -q 'RELEASE_TAG\|example\.invalid\|REPLACE_ME' "$RENDERED" \
  || { echo "deploy-k8s: overlay still contains placeholders (RELEASE_TAG / example.invalid / REPLACE_ME)"; exit 1; }
kubectl apply --dry-run=server -f "$RENDERED" > /dev/null
[ "$DRY" = "--dry-run" ] && { echo "deploy-k8s: dry run OK"; exit 0; }

PREVIOUS="$(kubectl get ns "$NS" -o jsonpath='{.metadata.annotations.openjcockpit\.io/release}' 2>/dev/null || true)"
echo "deploy-k8s: $PREVIOUS -> $TAG"

# Secret: only KEY=VALUE lines, never logged. Managed secret stores (External Secrets, Sealed Secrets,
# Vault) can replace this step by providing a Secret named openjcockpit-secrets with the same keys.
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
grep -E '^[A-Z0-9_]+=' "$ENV_FILE" | grep -vE '^(OPENJCOCKPIT_REGISTRY|OPENJCOCKPIT_IMAGE_TAG|OPENJCOCKPIT_HOST|KEYCLOAK_HOST|ACME_EMAIL|AGENT_WORKFLOWS_TENANT|OLLAMA_BASE_URL|OLLAMA_MODEL|BACKUP_DIR|BACKUP_RETENTION_DAYS|METRICS_TEXTFILE_DIR)=' \
  | kubectl -n "$NS" create secret generic openjcockpit-secrets --from-env-file=/dev/stdin --dry-run=client -o yaml \
  | kubectl apply -f - >/dev/null

kubectl -n "$NS" delete job openjcockpit-bootstrap --ignore-not-found >/dev/null
kubectl apply -f "$RENDERED"
kubectl -n "$NS" wait --for=condition=complete job/openjcockpit-bootstrap --timeout=900s
for d in $(kubectl -n "$NS" get deploy -o name); do kubectl -n "$NS" rollout status "$d" --timeout=900s; done

"$DIR/scripts/smoke-test.sh" k8s "$NS"
kubectl annotate ns "$NS" --overwrite "openjcockpit.io/release=$TAG" "openjcockpit.io/previous-release=${PREVIOUS:-none}" >/dev/null
echo "deploy-k8s: $TAG is live (rollback: deploy-k8s.sh $OVERLAY ${PREVIOUS:-<previous-tag>} $ENV_FILE)"
