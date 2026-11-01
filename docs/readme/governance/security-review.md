# Security review: production readiness (2026-10-27)

Scope: the application services, the local and production deployment profiles, job execution, identity
and authorization, policy enforcement, state durability, backup/restore and CI release gates. Method:
code and configuration review of the repository, plus executed checks (listed under *Evidence*). This is
an internal engineering review, not an independent penetration test or certification.

## Findings and resolution

| # | Severity | Finding | Resolution | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Critical | No production profile: every secret had a public local default; services would start with them anywhere. | Spring `prod` profile per service with no fallbacks; `ProductionConfigGuard` refuses placeholders, short secrets, the `local` profile and a non-HTTPS issuer; `validate-env.sh` checks env files before deploy. | `ProductionConfigGuardTest`; real image refused dev secrets and started with generated ones |
| 2 | Critical | `docker-socat` exposed the host Docker daemon (root-equivalent) to every container; the job container it served runs nothing. | Job containers off by default (dev and prod); proxy behind opt-in compose profile `docker-jobs`; no socket in any production manifest (CI greps for it). Launch hardened for opt-in use: read-only rootfs, no capabilities, `no-new-privileges`, no network by default, host/container network modes refused, CPU/memory/PID limits, `noexec` tmpfs. | `DockerContainerHardeningTest`; CI `deploy-config` |
| 3 | Critical | `PUT /api/opa-config` let any authenticated user disable OPA, switch to fail-open or point it at another server. | Admin-only (RBAC) **and** locked in production (`openjcockpit.opa.config-locked`, 403 + audit line). | `OpaConfigControllerTest`, `AuthorizationMatrixTest`, critical E2E |
| 4 | High | No authorization beyond authentication: any user could manage projects, credentials and decide approval gates. | Roles viewer / operator / approver / admin, deny-by-default URL rules in ai-control (unmapped routes denied, `anyRequest().denyAll()`), defence in depth in embabel-agent-service. | `AuthorizationMatrixTest` (749 + 443 cases), route-coverage test, critical E2E |
| 5 | High | Seeded users and passwords, demo projects and `localhost` redirect URIs would ship to any environment built from the repo. | Demo projects moved to a `local`-only Flyway location; production realm generated without users, with HTTPS-only, password policy, brute-force protection, no password grant, audit events. | Prod DB had no seed migrations; `render-prod-realm.py --check` in CI |
| 6 | High | Approval-gate state lived in memory: a restart silently lost paused runs. | Durable `agent_parked_runs`, exactly-once claim across instances, rehydration at startup. | `ParkedRunRestartIntegrationTest`, `DurableApprovalGateRegistryTest` |
| 7 | High | OPA default policy allowed any tool; a deny branch with an undefined reason fell through to *allow*. | Explicit guardrails: forbidden destructive operations (force push, reset, rebase, delete, exec/shell) and unknown tool families denied; reason made total. | 12 Rego tests (`opa test`), `opa check --strict` in CI, smoke test against the live OPA |
| 8 | Medium | Service images ran as root. | All images run as UID 10001 (dashboard 101); CI fails on a root image. | `id -u` on the built image; CI step |
| 9 | Medium | Health details and actuator on the public API port. | Separate management port 9090 (never routed); public health shows UP/DOWN only; `/actuator/prometheus` open only on that port. | Probed: 9090 readiness/prometheus 200, 9090 env 401, 8080 prometheus 401 |
| 10 | Medium | Playwright storage state with Keycloak session cookies committed under `e2e/`. | Removed from the index and ignored. Local-only identity; rotate nothing in production. | `git status` |
| 11 | Medium | E2E tests not blocking; reduced mode could not even start (`npm run dev` no longer existed). | Fixed the web server command; critical E2E set is a required part of `ci-summary`. | 25/25 critical tests passed locally against the stack |
| 12 | Medium | CORS origins hard-coded to localhost. | Configurable; empty in production (same-origin proxy). | `CorsConfigTest` |
| 13 | Low | Debug logging could include prompts. | `prod` sets INFO for `com.embabel.agent`; JSON logs. | config review |

## Residual risks (accepted for this release, tracked in Known limitations)

- **`git-mcp-server` has no authentication of its own.** Mitigated by network isolation only (internal
  network / NetworkPolicy allowing LiteLLM and embabel-agent-service).
- **Project-scoped authorization does not exist.** Roles apply to all projects of the deployment;
  separate deployments per tenant if projects must be isolated from each other's users.
- **embabel-agent-service read endpoints** accept any authenticated token without a role (reads through
  ai-control require viewer). It is not publicly routed in either profile.
- **Error messages** of `ai-control-service` and `embabel-agent-service` are returned to authenticated
  callers (stack traces are not).
- **Service-to-service traffic is plain HTTP** on the private network.
- **OpenAI key required** at boot even when only Ollama is used.
- **Single replica** for `embabel-agent-service` and `ai-control-service` (state reconciliation assumes
  one instance); in-flight agent executions end as `RUN_STATE_LOST` on restart (paused gates survive).
- **Data sent to LLMs** (spec content, repository files) — review against your data-protection rules.
- Container image CVEs are gated at release time (fixable CRITICAL) but not continuously rescanned.

## Approval status

| Item | Status |
| --- | --- |
| Engineering security review (this document) | Completed 2026-10-27 — critical and high findings resolved |
| Independent penetration test | **Not performed** — required before an internet-facing deployment that handles customer code |
| Release approval | Pending: the release owner signs the [release checklist](../operations/release-checklist.md) per release |
