#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/_lib.sh
source "${SCRIPT_DIR}/lib/_lib.sh"

usage() {
  cat <<'EOF'
Usage: stack-start.sh [--infra-only] [--with-local-marketplace]

Builds and starts the local Docker Compose stack (idempotent).

Options:
  --infra-only               Start only postgres and keycloak; prints the
                              extra command needed to run ai-control-service
                              locally and the Vite dev-server instructions
                              for the dashboard/landing pages.
  --with-local-marketplace   Append -f docker-compose.local-marketplace.yml.

Notes:
  * This script always rebuilds application images (--build); if there is
    substantial uncommitted work in the working tree, that work is baked
    into the rebuilt images.
  * No docker-compose.override.yml is ever created.
EOF
}

INFRA_ONLY=""
WITH_MARKETPLACE=""
for arg in "$@"; do
  case "$arg" in
    --infra-only) INFRA_ONLY="1" ;;
    --with-local-marketplace) WITH_MARKETPLACE="--with-local-marketplace" ;;
    -h|--help) usage; exit 0 ;;
    *) mf::die "Unknown argument: $arg (see --help)" ;;
  esac
done

cd "$(mf::repo_root)"
mf::acquire_lock
mf::require_docker
mf::require_env_file
mf::require_openai_key

COMPOSE_ARGS=()
while IFS= read -r part; do
  COMPOSE_ARGS+=("$part")
done < <(mf::compose_file_args ${WITH_MARKETPLACE})

mf::log_warn "This start rebuilds application images (--build). Any uncommitted working-tree changes are baked into the rebuilt images."

if [ -n "$INFRA_ONLY" ]; then
  mf::log_step "Starting infrastructure only: postgres, keycloak"
  docker compose "${COMPOSE_ARGS[@]}" up -d --build postgres keycloak

  mf::wait_for_http "Keycloak" "http://localhost:8080/realms/metafactory/.well-known/openid-configuration" 120 3

  mf::log_info "Infrastructure is up. To run ai-control-service locally against it, first create the metafactory database:"
  mf::log_info "  docker compose ${COMPOSE_ARGS[*]} run --rm postgres-init"
  mf::log_info "To run the dashboard/landing frontends locally, start Vite yourself, e.g.:"
  mf::log_info "  cd apps/dashboard && npm run dev"
  exit 0
fi

mf::log_step "Building and starting the full stack"
docker compose "${COMPOSE_ARGS[@]}" up -d --build

mf::log_step "Waiting for services to become healthy"
mf::wait_for_http "Keycloak" "http://localhost:8080/realms/metafactory/.well-known/openid-configuration" 120 3
mf::wait_for_http "ai-control-service" "http://localhost:9080/actuator/health" 180 3
mf::wait_for_http "embabel-agent-service" "http://localhost:8091/actuator/health" 180 3

mf::log_info "Stack is up."
mf::print_urls

exit 0
