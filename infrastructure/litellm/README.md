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
