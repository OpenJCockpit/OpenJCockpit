# OpenJCockpit documentation

The knowledge base for OpenJCockpit, the open-source AI Software Factory for governed agentic software
development. Start with the path that matches what you want to do. Every page links onwards to related
topics.

> New here? Read the [project README](../../README.md) first, then the [Overview](concepts/overview.md).

## By goal

| I want to… | Start here | Then |
| --- | --- | --- |
| **Understand the platform** | [Overview](concepts/overview.md) | [Architecture](concepts/architecture.md) |
| **Try it locally** | [Quickstart](getting-started/quickstart.md) | [Authentication](getting-started/authentication.md), [LLM providers](integrations/llm-providers.md) |
| **Build with agents and workflows** | [Agents and workflows](workflows/agents-and-workflows.md) | [Spec-driven development](workflows/spec-driven-development.md), [Agents and A2A](integrations/a2a-agents.md), [Git integration](workflows/git-integration.md), [Spec queue](workflows/spec-queue.md) |
| **Configure governance** | [Policy enforcement](governance/policy-enforcement.md) | [Approval gates](governance/approval-gates.md), [MCP tools](integrations/mcp-tools.md), [Security considerations](governance/security-considerations.md) |
| **Extend the platform** | [Agents and A2A](integrations/a2a-agents.md) | [Skills Hub](integrations/skills-marketplace.md), [MCP tools](integrations/mcp-tools.md), [Local development](development/local-development.md) |
| **Operate and troubleshoot** | [Troubleshooting](operations/troubleshooting.md) | [Runbooks](operations/runbooks.md), [Known limitations](operations/known-limitations.md), [Configuration reference](reference/configuration-reference.md) |
| **Contribute** | [CONTRIBUTING.md](../../CONTRIBUTING.md) | [Local development](development/local-development.md), [Testing](development/testing.md), [Continuous integration](development/continuous-integration.md) |
| **Assess it for my organization** | [Security considerations](governance/security-considerations.md) | [Known limitations](operations/known-limitations.md), [Policy enforcement](governance/policy-enforcement.md) |

## All pages

### Getting started

- [Quickstart](getting-started/quickstart.md): prerequisites, start the stack, first workflow run, stop and reset.
- [Authentication](getting-started/authentication.md): Keycloak, seeded local users, roles, service clients, login theme, realm re-import.

### Concepts

- [Overview](concepts/overview.md): the AI Software Factory, core concepts, the dashboard, vision vs. current status.
- [Architecture](concepts/architecture.md): components, technologies, design principles, request flow.
- [Agent runtime](concepts/agent-runtime.md): the execution plane, its contract with the agent service, security and failure semantics.

### Workflows

- [Agents and workflows](workflows/agents-and-workflows.md): execution model, default workflows, prompts, groups, export/import, chaining.
- [Spec-driven development](workflows/spec-driven-development.md): specs in project repositories and the spec folder rules.
- [Spec queue](workflows/spec-queue.md): sequential spec processing and auto-merge.
- [Git integration](workflows/git-integration.md): workspaces, branches, checkpoints, tickets, credentials, pull requests.

### Governance

- [Policy enforcement](governance/policy-enforcement.md): OPA, policy-guarded tools, guardrails, audit trails.
- [Approval gates](governance/approval-gates.md): human-in-the-loop decisions and the operations runbook.
- [Security considerations](governance/security-considerations.md): local-only credentials, risky postures, hardening starting points.

### Integrations

- [LLM providers](integrations/llm-providers.md): Ollama, LiteLLM, OpenAI, reverts, release notes.
- [MCP tools](integrations/mcp-tools.md): the Git MCP server, policy-checked tools, workspace isolation.
- [Agents and A2A](integrations/a2a-agents.md): agent catalogue, configuration-driven agents, A2A.
- [Skills Hub](integrations/skills-marketplace.md): skills and the dev-only marketplace double.

### Development

- [Local development](development/local-development.md): requirements, layout, running parts, verification.
- [Testing](development/testing.md): test layers and the Playwright E2E suite.
- [Continuous integration](development/continuous-integration.md): CI legs, `ci-summary`, other workflows.

### Operations

- [Runbooks](operations/runbooks.md): services, volumes, destructive commands, definition-file logging.
- [Remote agent runtime](operations/remote-agent-runtime.md): running `embabel-agent-runtime` on another host.
- [Troubleshooting](operations/troubleshooting.md): symptom → cause → fix.
- [Known limitations](operations/known-limitations.md): maturity, accepted risks and follow-ups.
- [Dependency management](operations/dependency-management.md): Renovate.

### Reference

- [Configuration reference](reference/configuration-reference.md): environment variables and defaults.
- [Historical notes](reference/historical-notes.md): superseded behaviour preserved for traceability.
- [Documentation migration map](reference/documentation-migration-map.md): where each former README section now lives.

## Other documentation in the repository

| Document | Topic |
| --- | --- |
| [docs-site](https://openjcockpit.github.io/OpenJCockpit/) ([source](../../docs-site/index.html)) | Public documentation site: about, spec queue details, illustrated first-run tutorial. |
| [`services/embabel-agent-service/docs/agent-integration/OPERATIONS.md`](../../services/embabel-agent-service/docs/agent-integration/OPERATIONS.md) | Authoritative operations guide for configuration-driven agents, A2A and git-workspace runs (Dutch). |
| [`services/embabel-agent-service/AGENTS.md`](../../services/embabel-agent-service/AGENTS.md) | Spec-driven workflow rules for agents. |
| [`services/embabel-agent-service/README.md`](../../services/embabel-agent-service/README.md) | Log keys and the workflow-definition write race statement. |
| [`frontend/e2e/README.md`](../../frontend/e2e/README.md) | Playwright E2E harness in full detail. |
| [`frontend/apps/dashboard/README.md`](../../frontend/apps/dashboard/README.md) and `frontend/libs/*/README.md` | Dashboard app and feature libraries. |
| [`agents/README.md`](../../agents/README.md) | Local agent catalogue format. |
| [`infrastructure/litellm/README.md`](../../infrastructure/litellm/README.md) | LiteLLM gateway configuration. |
| [`infrastructure/keycloak/local-users/README.md`](../../infrastructure/keycloak/local-users/README.md) | Personal local Keycloak users. |
| [SECURITY.md](../../SECURITY.md), [CONTRIBUTING.md](../../CONTRIBUTING.md), [CODE_OF_CONDUCT.md](../../CODE_OF_CONDUCT.md), [LICENSE](../../LICENSE), [NOTICE](../../NOTICE) | Project policies and licensing. |
