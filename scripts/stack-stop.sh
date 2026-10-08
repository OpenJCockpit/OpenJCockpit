#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/_lib.sh
source "${SCRIPT_DIR}/lib/_lib.sh"

usage() {
  cat <<'EOF'
Usage: stack-stop.sh [--with-local-marketplace]

Stops the local Docker Compose stack without deleting any data.
This script never removes a container, network, image, or volume;
it only calls "docker compose ... stop", which preserves all state
(the Keycloak realm, the openjcockpit database, and every named volume).

Options:
  --with-local-marketplace   Also target the mock-skills-marketplace overlay
                              (docker-compose.local-marketplace.yml).
EOF
}

WITH_MARKETPLACE=""
for arg in "$@"; do
  case "$arg" in
    --with-local-marketplace) WITH_MARKETPLACE="--with-local-marketplace" ;;
    -h|--help) usage; exit 0 ;;
    *) mf::die "Unknown argument: $arg (see --help)" ;;
  esac
done

cd "$(mf::repo_root)"
mf::acquire_lock
mf::require_docker

mf::log_step "Stopping the local stack (state is preserved, nothing is removed)"

if [ -n "$WITH_MARKETPLACE" ]; then
  RUNNING_COUNT="$(docker compose -f docker-compose.yml -f docker-compose.local-marketplace.yml ps --status running --quiet | wc -l | tr -d ' ')"
  if [ "$RUNNING_COUNT" != "0" ]; then
    docker compose -f docker-compose.yml -f docker-compose.local-marketplace.yml stop
    mf::log_info "Stack stopped. All data is preserved (Postgres, Keycloak, workflow-definitions, workflow-workspaces, git-mcp-workspaces)."
  else
    mf::log_info "Nothing appears to be running already; nothing to stop."
  fi
else
  RUNNING_COUNT="$(docker compose -f docker-compose.yml ps --status running --quiet | wc -l | tr -d ' ')"
  if [ "$RUNNING_COUNT" != "0" ]; then
    docker compose -f docker-compose.yml stop
    mf::log_info "Stack stopped. All data is preserved (Postgres, Keycloak, workflow-definitions, workflow-workspaces, git-mcp-workspaces)."
  else
    mf::log_info "Nothing appears to be running already; nothing to stop."
  fi
fi

exit 0
