# Known limitations and follow-ups

OpenJCockpit is under active development. This page collects the documented limitations, accepted
risks and named follow-ups, so you can judge what the current stack is and is not suitable for. Read it
together with [Security considerations](../governance/security-considerations.md).

## Maturity at a glance

| Area | Current state |
| --- | --- |
| Deployment | Local Docker Compose development stack only. No production deployment artefacts, image publishing or hardening guide. |
| Authentication | Keycloak with seeded local users and secrets. |
| Authorization | No fine-grained roles beyond `openjcockpit-admin` (spec queue auto-merge setting). Any authenticated user can approve gates. |
| Multi-tenancy | Tenant claim only for the local service client (`tenant_id=local`). |
| LLM providers | Local Ollama via LiteLLM plus OpenAI; `OPENAI_API_KEY` required to boot. No automatic provider fallback. |
| Approval persistence | Paused runs live in memory and do not survive a restart; decisions already made are persisted. |
| Tool security | `git-mcp-server` has no authentication of its own; reachable only via LiteLLM on the Compose network. |
| Policy | The default OPA policy is a permissive starting point. |
| Pull requests | Workflow runs push a branch; no default workflow opens a PR automatically. |
| Language focus | Java projects are the primary focus; other languages are not yet a verified target. |
| Testing | Unit/integration tests gate CI. The Playwright E2E suite runs in a non-blocking CI leg only. |

## Platform and integration limitations

- **Agent runtime boundary.** Agent executions are synchronous HTTP requests from `embabel-agent-service` to
  `embabel-agent-runtime` (no streaming, no asynchronous submit/poll, no cancellation of an agent that is
  already running — the same semantics as before the split). The runtime's execution registry is in memory
  per instance; the LiteLLM agent URLs must be changed by hand when the runtime moves. See
  [Agent runtime](../concepts/agent-runtime.md).

- **MCP transport pinned to SSE.** The MCP transport between `git-mcp-server` and its clients is
  explicitly pinned to the older SSE protocol (`spring.ai.mcp.server.protocol: sse` in `git-mcp-server`'s
  `application.yml`), because Spring AI 2.0.1 changed its default server transport to a newer
  "Streamable HTTP" transport and the `embabel-agent-service` client side was not migrated to it in that
  delivery. Migrating both ends to the newer Streamable HTTP transport is a deliberate, named follow-up
  for a future delivery, not an oversight. LiteLLM also registers the server over SSE.
- **`git-mcp-server` remains unauthenticated.** This is a pre-existing condition. It is now not
  published on the host and is reachable only through LiteLLM with explicitly granted keys, but the
  server itself still has no authentication. Hardening it (adding authentication) is a standing, known
  risk to address in a separate piece of work.
- **No Docker Compose healthchecks for the Java services.** Currently `postgres`, `keycloak` and
  `litellm` have Compose healthchecks; `ai-control-service`, `embabel-agent-service`, and `git-mcp-server`
  are instead verified via their `/actuator/health` endpoints. Adding Compose-level healthchecks for these
  three services would be a nice operational improvement, but it was deliberately not added when the
  framework version was bumped because it was not forced by that change; it is a candidate for a future,
  separate operational improvement.
- **Framework error-body `timestamp` notation changed.** Boot's own generic
  error response body (the fallback `{timestamp, status, error, message, path}` shape returned by
  `BasicErrorController` for uncaught exceptions) now renders its `timestamp` field using `Z` UTC-suffix
  notation (e.g. `2026-09-05T06:28:01.995Z`) instead of the pre-upgrade `+00:00` notation
  (e.g. `2026-09-04T15:15:01.306+00:00`): same instant, different notation, purely cosmetic. This field is
  not part of the documented `shared/contracts/openapi.yaml` `ApiErrorResponse` schema (which only has
  `code`/`message`), so it is outside the frozen contract surface, but any external consumer that parses
  this generic fallback error shape by exact string format (rather than as a standard ISO-8601 timestamp)
  would need to tolerate both notations.
- **`WWW-Authenticate` header value on 401 responses changed.** Both `ai-control-service` and
  `embabel-agent-service` now return
  `WWW-Authenticate: Bearer resource_metadata="http://<host>/.well-known/oauth-protected-resource"` on
  unauthenticated requests, instead of the pre-upgrade bare `WWW-Authenticate: Bearer`. This is a Spring
  Security 7.1.1 feature (RFC 9728 protected-resource metadata discovery), and the URL value is derived
  from the request's own `Host` header, so frontend/API consumers should not depend on the exact header
  value being the bare string `Bearer`. Note that the RFC 9728 metadata endpoint itself
  (`/.well-known/oauth-protected-resource`) that Spring Security 7.1.1 auto-registers has been explicitly
  locked down to require authentication (via a dedicated higher-priority security filter chain on both
  services), since by default it would have introduced a new anonymous endpoint. This was found and fixed
  during that delivery's integration review, not left as a known limitation to defer.

## LLM and gateway limitations

- **A wrong or missing LiteLLM gateway key produces two gateway requests, not one.** Both are correctly
  rejected with 401, fail-closed, and no credential is ever leaked; this is bounded (`max-attempts=2`),
  not an unbounded retry loop. Root cause: the outer retry wrapper (`embabel-agent-api`'s
  `LlmDataBindingProperties`) is a `final` third-party class shared across all LLM routes, with no
  extension point to exclude authentication failures from its generic retry policy. This was
  investigated and found to have no clean fix confined to that delivery's scope, without either misusing
  an unrelated exception type or silently changing shared retry behaviour for the unrelated
  OpenAI/`gpt41Mini` path. Accepted as a documented residual risk, not fixed. Follow-up: request an
  upstream `embabel-agent-api` enhancement (a predicate/exception-registration hook on
  `LlmDataBindingProperties`, or a per-route retry-template override point), or accept a scoped
  compensating control at the LiteLLM/infrastructure layer if this becomes operationally significant.
- **MCP tool-call fidelity through the LiteLLM gateway was not exercised end-to-end during the original
  manual verification.** The default/coding LLM's tool-calling behaviour through the gateway
  (`ollama_chat` provider) is architecturally sound and was verified via static analysis of the
  provider's request/response transformation, but a real MCP tool call (e.g. a branch/commit/PR tool
  invocation) was not exercised through it, because doing so would require pushing to a real Git
  repository with real credentials. Nothing was provisioned or authorized for that in the verification
  environment, consistent with this repository's own rule against unauthorized git mutations. Recovery if
  this proves broken in practice is the documented Revert B (direct host-Ollama routing; see
  [LLM providers](../integrations/llm-providers.md#reverting-the-default-llm)). Follow-up: exercise a full
  multi-stage agent run with an MCP tool call through the gateway in a properly sandboxed environment
  before relying on this path for tool-heavy workflows.
  **Update:** the LLM-only path (a single-stage agent run's default/coding LLM call transiting the gateway
  to host Ollama, positively attributed to the expected virtual key and model) is covered by an
  automated, precondition-gated Playwright check (`npm run e2e:llm`; see
  [`frontend/e2e/README.md`](../../../frontend/e2e/README.md)'s "The agent ⇄ LiteLLM ⇄ Ollama live check").
  **Update (A2A delivery, 2026-10-15):** the agent-integration operations guide records a live
  verification in which agents called `git_list_files`/`git_read_file` through LiteLLM in a job workspace
  without policy refusal. Automated end-to-end coverage of MCP tool calls through the gateway remains
  absent.
- **No automatic fallback between LLM providers.** Both reverts are manual.
- **Health is not readiness for inference.** `/actuator/health` reports `UP` when Ollama is unreachable.
- **A2A limitations.** Only A2A 0.3 and `message/send`; LiteLLM remote agent content is `UNSUPPORTED`;
  gateway guardrail execution per call is not live-verified. See
  [A2A and configuration-driven agents](../integrations/a2a-agents.md#limitations).

## Workflow and approval limitations

- **Paused runs do not survive a restart** of `embabel-agent-service`; in-memory git-workspace tickets and
  run scopes are lost too. See [Approval gates](../governance/approval-gates.md#in-flight-paused-runs-do-not-survive-a-restart-or-redeploy).
- **A job stops its run after `WORKFLOW_AGENT_TIMEOUT`**, even while an approval gate is waiting.
- **Orb-child workflows do not inherit the Git workspace.**
- **A job running during an `ai-control-service` restart stays at `AGENTS_RUNNING`.**
- **The job container image is not pulled automatically** (`docker pull eclipse-temurin:25-jre`).
- **`ExecutionConfig.timeoutSeconds` is dead configuration**: persisted and exposed in the API contract
  but never read by any code.
- **Spec queue**: one active item per project; unknown start outcomes halt the queue; auto-merge requires
  exactly one `pull-request:` run artifact, which the default workflows do not emit. See
  [Spec queue](../workflows/spec-queue.md#residual-risks).
- **No cost meter, rate limit or token budget** beyond bounded feedback iterations and chain depth.

## Testing and CI limitations

- **The Playwright E2E suite is not a blocking CI gate.** `frontend/e2e/` (see
  [Testing](../development/testing.md)) is a local and QA-agent-run gate. A separate `e2e-ci.yml` workflow
  runs the suite in reduced mode as a **non-blocking, informational** leg that is deliberately not wired
  into the required `ci-summary` check and does not upload artefacts (they can contain live tokens).
  Making E2E a blocking CI gate is a stated follow-up.
- E2E coverage gaps (spec queue, positive admin-role coverage, `WorkflowPromptDialog` dialog semantics,
  wrong-password scenario, no ESLint in `e2e/`) are listed in
  [`frontend/e2e/README.md`](../../../frontend/e2e/README.md#what-is-deliberately-excluded-and-why).

## Skills Hub

- The marketplace wire contract is a placeholder; no real marketplace vendor exists yet.
- A stored marketplace URL ending in `/skills` produces a double `/skills/skills` request. See
  [Skills Hub](../integrations/skills-marketplace.md#known-limitation-double-skills-if-the-stored-url-already-ends-in-skills).

## Related

- [Security considerations](../governance/security-considerations.md)
- [Troubleshooting](troubleshooting.md)
