#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/_lib.sh
source "${SCRIPT_DIR}/lib/_lib.sh"

CONFIRM_TOKEN="RESET-WORKFLOWS"

usage() {
  cat <<'EOF'
Usage: stack-clean-start.sh [--yes]

Scoped reset of ONLY the workflow and workflow-group seed data, followed by
a rebuild/restart of embabel-agent-service so the seed bundle is re-imported.

This is the ONLY script in this repository permitted to delete anything.
It clears exactly two directories inside the workflow-definitions volume:
  - workflows/         (only depth-1 *.yaml files; the directory itself stays)
  - workflow-groups/    (only depth-1 *.yaml files; the directory itself stays)

It never removes the workflow-definitions volume and never touches:
  agents/, subagents/, skills/, decision-logs/ (OPA policy audit trail),
  approval-decisions/ (approval-gate audit trail), workflow-workspaces,
  git-mcp-workspaces, Postgres, or Keycloak.

Options:
  --yes   Skip the interactive typed-confirmation prompt (the full
          clear/preserve inventory is still printed first either way).
EOF
}

AUTO_YES=""
for arg in "$@"; do
  case "$arg" in
    --yes) AUTO_YES="1" ;;
    -h|--help) usage; exit 0 ;;
    *) mf::die "Unknown argument: $arg (see --help)" ;;
  esac
done

cd "$(mf::repo_root)"
mf::acquire_lock
mf::require_docker

RESET_SCRIPT="${SCRIPT_DIR}/lib/reset-workflow-definitions.sh"

mf::log_step "Reading the current workflow-definitions inventory (no changes made yet)"
docker compose -f docker-compose.yml run --rm -T --no-deps \
  --entrypoint /bin/sh embabel-agent-service \
  -s -- inventory < "$RESET_SCRIPT"

mf::log_warn "The two directories above (workflows/, workflow-groups/) will be cleared."
mf::log_warn "Everything else listed as PRESERVE above (both audit trails, all three spec directories, both workspace volumes, Postgres, and Keycloak) is left untouched."

if [ -z "$AUTO_YES" ]; then
  if ! mf::confirm "$CONFIRM_TOKEN"; then
    mf::die "Confirmation not given (expected exact text: $CONFIRM_TOKEN). Nothing was deleted."
  fi
else
  mf::log_info "Skipping interactive confirmation (--yes was given); the inventory above was still shown."
fi

mf::log_step "Stopping embabel-agent-service before clearing seed data"
docker compose -f docker-compose.yml stop embabel-agent-service

mf::log_step "Clearing workflows/ and workflow-groups/ (only *.yaml files, depth 1)"
docker compose -f docker-compose.yml run --rm -T --no-deps \
  --entrypoint /bin/sh embabel-agent-service \
  -s -- clear < "$RESET_SCRIPT"

mf::log_step "Rebuilding and restarting the stack so the seed bundle re-imports"
docker compose -f docker-compose.yml up -d --build

mf::wait_for_http "embabel-agent-service" "http://localhost:8091/actuator/health" 180 3

mf::log_info "Clean-start complete. The seed bundle has been re-imported by DefaultWorkflowImporter's startup ApplicationRunner."
mf::log_info "Verify manually with:"
mf::log_info "  curl -s http://localhost:8091/api/workflows | jq '.[] | select(.id==\"wf-spec-realise\") | .agentIds'"
mf::log_info "  Expected: [\"impact\",\"test-design\",\"implementation\",\"review\",\"realisation\",\"evidence\"]"

exit 0
