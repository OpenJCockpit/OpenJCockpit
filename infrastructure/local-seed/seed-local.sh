#!/bin/sh
# One-shot local development seed, run by the docker-compose `local-seed` service on every
# `docker compose up`. Idempotent: existing entries are reconciled, never duplicated.
#   1. Keycloak: service clients `openjcockpit-a2a` (tokens carry tenant_id=local), `openjcockpit-git-mcp`
#                and `openjcockpit-ai-control` (all client_credentials only)
#   2. LiteLLM:  model key of embabel-agent-service (+ git MCP server), the one A2A key `openjcockpit-a2a`
#                and the git MCP key of ai-control-service
# The agent-service database part (tenant 'local' bindings) is seeded by Flyway in Spring profile
# `local` (db/seed-local), because it needs the service's own migrations first.
set -e
dir=$(dirname "$0")
sh "${dir}/keycloak-service-clients.sh"
sh "${dir}/litellm-keys.sh"
echo "local-seed: done"
