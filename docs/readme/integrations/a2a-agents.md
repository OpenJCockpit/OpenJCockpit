# Agents: catalogue, configuration-driven agents and A2A

This page is an English overview of how agents are defined and served. The authoritative, detailed
operations guide (in Dutch) is
[`services/embabel-agent-service/docs/agent-integration/OPERATIONS.md`](../../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md).
When in doubt, that guide and the code win.

## The agent catalogue

Workflows are composed of references to agents in a central **agent catalogue**, visible in the
dashboard under **Workflow design → Agents** (`GET /api/agent-catalog`). The catalogue has one
section per source:

| Section | Source | Workflow reference |
| --- | --- | --- |
| **Local** | Markdown agent definitions in the repository's [`agents/`](../../../agents/README.md) folder (mounted at `/agents`, recursive; subfolders become the hierarchy shown in the dashboard). | `local:<agent-id>` |
| **One section per A2A provider** (locally: **LiteLLM**) | Agents served over the [Agent2Agent (A2A)](https://a2a-protocol.org/) protocol by a provider gateway. **Details** fetches the agent card live. | `a2a:<provider>:<agent>`, e.g. `a2a:litellm:qa-engineer` |

Additional LiteLLM-compatible A2A gateways can be added in the dashboard under
**Workflow designer → Configuration → A2A services**. Their secrets are stored encrypted (AES-GCM)
with `A2A_SERVICE_ENCRYPTION_SECRET` and `A2A_SERVICE_ENCRYPTION_SALT`; without that key nothing can
be stored (fail closed).

## Defining an agent

An agent is one Markdown file: YAML front matter followed by the agent's instructions.

```markdown
---
id: my-agent                 # required, lowercase kebab-case, unique across this folder
name: "My Agent"             # optional display name
version: "1.0.0"             # required
description: "What it does"  # optional
model: agent-chat            # required, a configured model alias
skills: []                   # optional skill ids
tools: []                    # optional tool ids (granted only server-side)
guardrails: [pii-protection] # optional
---
# Role
...
```

See [`agents/README.md`](../../../agents/README.md) for the local catalogue rules. Invalid files are
still listed in the dashboard, marked with the problem.

## Where A2A is served

Inbound A2A (`/a2a/{agentId}` and the Agent Cards under `/a2a/{agentId}/.well-known/`) is hosted by
**`embabel-agent-runtime`** (host port 8092 locally), not by `embabel-agent-service`. The advertised URL is
`A2A_PUBLIC_BASE_URL` (locally `http://embabel-agent-runtime:8082`); the `url` of each agent in
[`infrastructure/litellm/config.yaml`](../../../infrastructure/litellm/config.yaml) must equal
`A2A_PUBLIC_BASE_URL/a2a/<agent>`. Outbound A2A — the provider clients that list agents for the catalogue and
call `a2a:` agents in workflows — stays in `embabel-agent-service`. See [Agent runtime](../concepts/agent-runtime.md).

**Migration note.** Before the split the endpoints were at `http://embabel-agent-service:8081/a2a/...`
(host port 8091). Callers going through LiteLLM need no change; direct callers must use the runtime's
address. The protocol, card content, error codes and authentication are unchanged.

## The seeded agents

The local stack seeds four role-based agents, defined in
[`services/embabel-agent-runtime/agent-definitions/agents/`](../../../services/embabel-agent-runtime/agent-definitions/agents/)
(shipped in the `openjcockpit/embabel-agent-runtime` image) and served over A2A through LiteLLM by
`embabel-agent-runtime`:

| Agent | Role | Git tools |
| --- | --- | --- |
| `business-analyst` | Turns a business request into clear, testable requirements and acceptance criteria | `git.list_files`, `git.read_file` |
| `solution-architect` | Designs a solution for given requirements: components, boundaries, integrations and trade-offs | `git.list_files`, `git.read_file` |
| `software-developer` | Implements a designed change as small, reviewable code changes with tests | `git.list_files`, `git.read_file`, `git.write_file` |
| `qa-engineer` | Designs test strategy and test cases and assesses whether acceptance criteria are met | `git.list_files`, `git.read_file`, `git.write_file` |

A `customer-support` example agent with a `customer-lookup` skill is also included.

The default workflows use these agents. See
[Agents and workflows](../workflows/agents-and-workflows.md#default-workflows-automatically-imported).

## How a configuration-driven agent runs

```text
A2A client ──► LiteLLM A2A gateway (/a2a/<agent>)
                 ▼
embabel-agent-runtime  POST /a2a/{agentId}   (A2A 0.3, JSON-RPC, message/send only)
  ├─ JWT validation → tenant from the configured tenant claim
  ├─ workflow run id header (engine identity only) → run scope from embabel-agent-service
  ├─ AgentDefinitionResolver (once per run)
  │    tenant binding (embabel-agent-service, internal API): enabled agents, tool allowlist, mandatory guardrails, pins
  │    source order: REMOTE → LAST_KNOWN_GOOD → LOCAL definition
  │    ⇒ immutable resolved definition: skills, tools (server registry ∩ allowlist), guardrails, model alias
  ├─ Embabel agent with the resolved prompt, skills, call-time-checked tools (OPA decision by
  │    embabel-agent-service per call) and guardrails
  └─ LLM call via the LiteLLM model gateway (model = alias)
```

Key properties, as documented in the operations guide:

- **Tools are granted server-side only.** Tool access requires both the agent definition and the
  tenant binding's allowlist, and each call is re-checked at call time (including OPA).
- **Guardrails fail closed.** A guardrail id without an operational executor blocks the run
  (`GUARDRAIL_MISSING`). The built-in `pii-protection` guardrail checks LLM input and output.
- **Snapshots cannot restore revoked rights.** Every chosen definition is validated against the
  *current* binding, tools, guardrails and model.
- The Embabel service calls LiteLLM's **model** route for its own LLM calls, never its own A2A route,
  so there is no loop.

## Limitations

From the operations guide (§11):

- LiteLLM has no API for agent Markdown or skill package content; remote content is `UNSUPPORTED`,
  so the local source provides content.
- A2A: only version 0.3 and `message/send`; no streaming, tasks or push notifications, no
  authenticated extended card.
- The tenant claim exists only for the local service client `openjcockpit-a2a` (hard-coded
  `tenant_id=local`). Real users and tenants still need a Keycloak mapper per tenant.
- `logging.level.com.embabel.agent: DEBUG` (existing setting) can log prompt content; `INFO` is
  recommended in production.
- `LiteLlmPolicyGuardrailExecutor` proves attachment configuration via `/policies/resolve`, not actual
  per-call execution; whether the policy engine is available in the LiteLLM edition used has not been
  live-verified.

## Try it

With the stack running:

```bash
./scripts/a2a-roundtrip.sh        # calls the four seeded agents through the LiteLLM A2A gateway (:4100)
```

## Related

- [Agents and workflows](../workflows/agents-and-workflows.md)
- [Policy enforcement](../governance/policy-enforcement.md)
- [LLM providers](llm-providers.md)
- [Skills Hub](skills-marketplace.md)
