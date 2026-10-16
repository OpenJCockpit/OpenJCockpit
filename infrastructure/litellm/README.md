# LiteLLM gateway (local Ollama proxy)

Local-only LiteLLM proxy that fronts the Ollama model for the agent service.

## Pinned image

`ghcr.io/berriai/litellm:v1.101.2`

`config.yaml` is the single source of truth for the model list and runtime
policy. It is mounted read-only at `/app/config.yaml` and loaded via
`CONFIG_FILE_PATH`. No credentials live in the file; every credential arrives
via the environment.

## What config.yaml pins, and why

- **BR-19 timeout/retry stack (layers 7-12):** per-request `timeout`,
  `stream_timeout`, `request_timeout`, and `num_retries: 0` at both the
  `litellm_settings` and `router_settings` levels, plus `disable_cooldowns`
  for the single deployment. The goal is a deterministic, non-retrying, fully
  bounded request path.
- **BR-9 privacy:** `turn_off_message_logging: true` and
  `redact_messages_in_exceptions: true` so prompt text never lands in logs or
  error output.

## Git MCP server

`config.yaml` registers the local `git-mcp-server` under `mcp_servers.openjcockpit_git` (SSE,
`http://git-mcp-server:8093/sse`, pinned `server_id`, `allow_all_keys: false`, private network only).
`local-seed` grants it to the `embabel-agent-service` key (agents' git tools) and the `ai-control-service` key
(git-workspace jobs; MCP REST bridge routes only). Clients use LiteLLM's `/mcp`, `/sse` or `/mcp-rest/tools/*`;
tools appear as `openjcockpit_git-<tool>`. The server is not published on the host, and no tool takes a git
credential: job workspaces (`workspaceKey=job-<uuid>`) get theirs from ai-control server-side. Quick check:

```bash
curl -s -H "Authorization: Bearer $LITELLM_VIRTUAL_KEY" http://localhost:4100/mcp-rest/tools/list
```

## Local-only seed credentials

All credentials used here -- `LITELLM_MASTER_KEY`, `LITELLM_SALT_KEY`,
`LITELLM_VIRTUAL_KEY`, and the admin UI pair `UI_USERNAME`/`ricky` and
`UI_PASSWORD`/`Welkom01!` -- are **local-testing-only seed credentials** under
the CLAUDE.md seed-credential exception. **Never reuse them in CI, staging, or
production.**

## Salt-key rotation hazard (R-8)

Rotating `LITELLM_SALT_KEY` invalidates every credential already encrypted in
LiteLLM's Postgres database. Treat the salt key as a long-lived local secret
and do not rotate it casually.

## Admin UI authentication gap

The admin UI on port 4100 authenticates against LiteLLM's own internal
credential store, **not** Keycloak. This is an accepted, documented
local-development-only gap. Keycloak/OIDC SSO is an out-of-scope follow-up
and requires LiteLLM's commercial tier.

## Accessibility (AC-19)

Vendor admin-UI accessibility (WCAG) conformance is **explicitly out of
scope** for this delivery (AC-19).
