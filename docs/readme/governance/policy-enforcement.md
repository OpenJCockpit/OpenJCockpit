# Policy enforcement and governance controls

OpenJCockpit is designed to support governed AI-driven software engineering: agents act inside
explicit, inspectable boundaries instead of having open-ended access. This page describes the
controls that are implemented today, and where they stop.

| Control | What it governs | Where |
| --- | --- | --- |
| **OPA policy decisions** | Workflow starts, agents, skills, subagents and tool calls | [Open Policy Agent](https://www.openpolicyagent.org/) server + `embabel-agent-service` policy client |
| **Policy-guarded tool gateway** | Every MCP tool call passes an OPA check first | `PolicyGuardedMcpToolGateway`, `SnapshotBoundTool` → `OpaToolCallAuthorizer` |
| **Tool allowlists** | Which tools an agent may use at all | Agent definition (`tools:`) **and** tenant binding (`agent_tenant_binding_tools`) |
| **Guardrails** | LLM input/output content (e.g. PII) | `pii-protection` guardrail, optional LiteLLM gateway guardrails; fail closed |
| **Human approval gates** | A human accepts, denies or requests changes inside a run | [Approval gates](approval-gates.md) |
| **Audit trails** | Policy decisions and approval decisions | `decision-logs` and `approval-decisions` in the `workflow-definitions` volume |
| **Server-side credentials** | Git credentials never reach LiteLLM or an LLM | [Git integration](../workflows/git-integration.md#git-credentials-never-reach-litellm-or-an-llm) |
| **Cost and blast-radius bounds** | Feedback iterations, chain depth, orbs per workflow | [Approval gates](approval-gates.md#the-three-tunables), [workflow chaining](../workflows/agents-and-workflows.md#workflow-chaining-workflow-orbs) |

## Open Policy Agent (OPA)

The local stack runs an OPA server (`openpolicyagent/opa`, port `8181`) with policies mounted as plain
files from [`infrastructure/opa/policies/`](../../../infrastructure/opa/policies/), so edits are picked up on
container restart without a rebuild. Every decision is printed to stdout (`docker compose logs opa`).

### Policy client configuration (`openjcockpit.opa.*`)

| Variable | Default (`application.yml`) | Meaning |
| --- | --- | --- |
| `OPA_ENABLED` | `false` (Compose: `true`) | Enables policy decisions. **When disabled, every decision is allowed** (audited as `SKIPPED_DISABLED`). |
| `OPA_BASE_URL` | `http://localhost:8181` (Compose: `http://opa:8181`) | OPA server URL. |
| `OPA_POLICY_PATH` | `/v1/data/openjcockpit/workflow/decision` | Decision document queried. |
| `OPA_HEALTH_PATH` | `/health` | Health endpoint. |
| `OPA_TIMEOUT_SECONDS` | `3` | Call timeout. |
| `OPA_FAIL_MODE` | `FAIL_CLOSED` | Behaviour when OPA is enabled but unreachable or fails (audited as `POLICY_UNAVAILABLE`). |
| `OPA_DECISION_LOGGING_ENABLED` | `true` | Write policy decisions to the audit trail. |
| `OPA_DECISION_LOG_EXPORT_ENABLED` | `true` | Allow exporting decision logs. |

The OPA client settings can also be viewed and changed in the dashboard's Workflow Configuration UI
(`/api/opa-config`).

### The default policy is a starting point

[`workflow_decision.rego`](../../../infrastructure/opa/policies/workflow_decision.rego) is explicitly
*a starting point, not final governance logic*. Rules are evaluated top to bottom:

1. **Missing identifiers** (`workflowId` or `customerId`) → denied, risk `medium`.
2. **Restricted project** (`projectClassification == "restricted"`) without human approval → allowed,
   with `requiredApproval: true`, risk `high`.
3. Otherwise → **default allow**, risk `low`.

Each decision returns `allowed`, `reason`, `requiredApproval`, `riskLevel` and `auditTags`, which are
recorded in the decision log. Replace or extend these rules with your organization's real approval and
risk requirements before relying on them.

> Agent runs outside a workflow (for example a standalone A2A call) have no `workflowId` and are
> therefore denied tool access by this policy. Every workflow run carries its workflow id and execution
> id to its agents for this purpose.

## Policy-guarded tool execution

Agents only reach tools through a policy-guarded path:

- **Bind time:** an agent receives a tool only if both its definition and its tenant binding allow it
  (`ToolAccessPolicy`). Text in instructions, prompts or skills never grants a tool.
- **Call time:** each tool call is re-authorized against the agent's resolved snapshot and OPA
  (`SnapshotBoundTool` → `OpaToolCallAuthorizer`); the central MCP client routes calls through
  `PolicyGuardedMcpToolGateway`. With OPA disabled, only the allowlist applies.
- **Tool arguments are not trusted for scope:** Git tools use the run's repository and workspace, never
  LLM-chosen values. See [MCP tools](../integrations/mcp-tools.md).

## Guardrails

| Id | Coverage | Fail closed |
| --- | --- | --- |
| `pii-protection` | LLM input and output of the run | Yes: a violation ends the call with `GUARDRAIL_VIOLATION`. |
| Configured gateway guardrail | LLM calls to a model alias via LiteLLM | Yes; only operational once LiteLLM confirms it via `/policies/resolve`. |
| Tool access | Every tool call | Yes; with OPA off, allowlist only. |

A guardrail id without an operational executor blocks the run (`GUARDRAIL_MISSING`); a policy id that
only appears in a prompt or YAML does not count. Details:
[A2A and configuration-driven agents](../integrations/a2a-agents.md).

## Audit trails

Policy decisions (`decision-logs`) and approval decisions (`approval-decisions`) are stored as files in
the `workflow-definitions` volume and can be queried per workflow and execution
(`/api/workflows/{id}/decision-logs`, `/api/workflow-executions/{id}/decision-logs`,
`/api/agent-runs/{runId}/approval-decisions`).

> **A corrupt audit record is skipped, not reported.** A malformed or empty audit file is skipped and
> logged (`definition.file.read.failed`) instead of failing the listing, so it becomes silently absent
> from an audit listing. See [Runbooks](../operations/runbooks.md#workflow-definition-file-logging).

## What is not covered (yet)

These are important for any enterprise assessment:

- **No fine-grained authorization.** Apart from `openjcockpit-admin` (spec queue auto-merge setting),
  any authenticated user can start workflows and approve, deny or comment on approval gates.
- **The default OPA policy is permissive** (default allow) and not tailored to any organization.
- **`requiredApproval` in a policy decision is recorded**, but approval gates are configured per
  workflow; verify the end-to-end behaviour you need before relying on policy-driven approvals.
- **No cost meter, rate limit or token budget** beyond the bounded iterations and chain depth.
- **Audit records are files** in a Docker volume, not a tamper-evident store.

See [Security considerations](security-considerations.md) and
[Known limitations](../operations/known-limitations.md).

## Related

- [Approval gates](approval-gates.md)
- [MCP tools](../integrations/mcp-tools.md)
- [A2A and configuration-driven agents](../integrations/a2a-agents.md)
