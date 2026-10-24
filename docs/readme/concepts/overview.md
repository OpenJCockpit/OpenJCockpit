# OpenJCockpit overview

OpenJCockpit is an **open-source AI Software Factory**: an AI Orchestration Dashboard and backend
platform for **governed agentic software development**. It coordinates AI agents in structured,
specification-driven workflows, with policy checks, human approval gates and reviewable Git delivery
around them.

This page explains the concepts, separates the long-term vision from what is implemented today, and
points to the detailed documentation.

## Why an AI Software Factory?

A single coding agent can generate code. Delivering production-quality software takes more:
requirements, architecture, coordinated implementation, testing, review, policy and controlled
delivery. OpenJCockpit's approach is to put those activities into **orchestrated workflows** of
specialised agents, each with explicit permissions, and to keep humans in control of the decisions that
matter.

| From | Towards | How OpenJCockpit approaches it today |
| --- | --- | --- |
| AI-assisted coding | Orchestrated AI software engineering | Workflows chain role-based agents (analyst, architect, developer, QA), each building on the previous outputs. |
| Individual AI agents | Coordinated development workflows | A central agent catalogue, workflow groups, workflow chaining and a per-project spec queue. |
| Unstructured prompting | Specification-driven engineering | Specs in the repository are the input contract for implementation workflows. |
| Autonomous code generation | Governed, reviewable delivery | OPA policy checks, tool allowlists, guardrails, approval gates and one Git branch per run with checkpoint commits. |
| Experimental AI workflows | Enterprise engineering practices | Keycloak identity, audit trails, CI with a 100% backend line-coverage gate, documented limitations. Production hardening is still ahead. |

## Core concepts

| Concept | What it is | Read more |
| --- | --- | --- |
| **AI Orchestration Dashboard** | The web UI where you select a project, design and start workflows, follow agent runs, take approval decisions and manage specs, skills and queues. | [Below](#the-ai-orchestration-dashboard) |
| **Project** | A software project with its Git repository and credentials. Runs on a project work in that repository. | [Git integration](../workflows/git-integration.md) |
| **Spec** | A Markdown specification with requirements and acceptance criteria, stored in the project's `specs/` folder. | [Spec-driven development](../workflows/spec-driven-development.md) |
| **Agent** | A role-based AI agent (for example Software Developer) with a model alias, instructions, skills, tools and guardrails. Defined locally in Markdown or served over A2A. | [Agents](../integrations/a2a-agents.md) |
| **Workflow** | An ordered chain of agents, optionally with approval gates and workflow orbs. | [Agents and workflows](../workflows/agents-and-workflows.md) |
| **Approval gate** | A human decision point inside a run: accept, deny or accept with comments. | [Approval gates](../governance/approval-gates.md) |
| **Policy** | OPA rules evaluated for workflow starts, agents and every tool call. | [Policy enforcement](../governance/policy-enforcement.md) |
| **Tool** | A capability an agent may call, such as reading or writing repository files, via MCP. | [MCP tools](../integrations/mcp-tools.md) |
| **Skill** | A reusable instruction package for agents (Agent Skills format). | [Skills Hub](../integrations/skills-marketplace.md) |
| **Spec queue** | A per-project queue that runs specs one at a time, optionally auto-merging results. | [Spec queue](../workflows/spec-queue.md) |

## The AI Orchestration Dashboard

The dashboard is a React application behind Keycloak login. After signing in, you see an animated
landing hub (an intro greeting with your first name, a top bar with Profile, Notifications, Messages
and Logout, and two portals: **Business** and **Implementation**). From there you work with:

| Area | What you can do |
| --- | --- |
| **Project selection and settings** | Choose a project; manage its repository and Git credentials. |
| **Workflow dashboard and overview** | See the workflows available for the project and start them, with a prompt when the workflow asks for one. |
| **Workflow Design** | Compose workflows from catalogue agents (Local / LiteLLM sections), set the execution order, add approval gates and workflow orbs, group workflows, export/import JSON. Browse agents, skills and A2A services. |
| **Workflow Configuration** | Configure the OPA policy client and A2A services. |
| **Workflow execution and history** | Follow a run live: agent sequence, agent logs and outputs, events, policy decisions, Git branch and status ("run state lost" for unknown runs). |
| **Approval decisions** | Review the approval context (including changed files) and accept, deny or accept with comments. |
| **Spec files** | Browse the project's specs, run the spec-init flow, create a spec from a prompt. |
| **Spec queue** | Enqueue, reorder, pause, retry or skip specs; admins control auto-merge. |
| **Skills Hub** | Browse skills and register Skills Marketplace connections. |

The UI uses a self-authored, futuristic animated/framed look, also applied to the custom Keycloak login
theme. See [`frontend/libs/ui/README.md`](../../../frontend/libs/ui/README.md).

### Original feature list

The original README listed the following under "What is in this project?". It is preserved here with
corrected paths:

- React/Vite frontend with a self-authored animated/framed UI (see `frontend/libs/ui/README.md`).
- Keycloak-authentication with `keycloak-js`.
- The landing page only shown with a valid auth token.
- Intro-animation with `Hi <name>!`, where the name is retrieved from the Keycloak token (the code uses
  the first name).
- Topbar with Profile, Notifications, Messages and Logout.
- Two glow-spheres: Business and Implementation.
- Keycloak realm-import file: `infrastructure/keycloak/openjcockpit-realm.json`.
- PostgreSQL as database for Keycloak (and for the application services and LiteLLM).
- Custom Keycloak login theme: `infrastructure/themes/openjcockpit/login`.
- The realm `openjcockpit` uses `loginTheme: openjcockpit` by default.

## Vision and current status

OpenJCockpit is ambitious in direction and explicit about its current state.

| Capability | Status |
| --- | --- |
| Orchestrated, multi-agent workflows from a dashboard | **Implemented** |
| Agent catalogue with local Markdown agents and A2A agents via LiteLLM | **Implemented** |
| Spec-driven workflows (spec init, spec from prompt, implement, realise) | **Implemented** |
| OPA policy checks for workflow starts, agents and tool calls; audit trail | **Implemented**; default policy is a permissive starting point |
| Human approval gates with bounded feedback loops | **Implemented**; paused runs are in memory only |
| Git delivery: one branch per run with checkpoint commits, server-side credentials | **Implemented** |
| Automatic pull request creation for workflow runs | **Not in the default workflows** (a PR tool exists in the Git MCP server) |
| Spec queue with optional auto-merge | **Implemented**; auto-merge needs a run-reported PR |
| Skills Marketplace integration | **Early**: placeholder contract, dev-only mock |
| Fine-grained roles, multi-tenancy, production deployment | **Not yet** |
| Java application development | **Primary focus** |
| Other programming languages | **Extensibility opportunity**: agents work on repository files and are configurable, but non-Java projects are not a verified target yet |

## Java first, language-extensible by design

OpenJCockpit is built in Java (Spring Boot, Embabel, Spring AI) and focuses primarily on Java application
development. The architecture does not hard-wire a language into the workflow engine: agents are
configurable Markdown definitions with instructions, skills and tools, and the Git tools operate on
plain repository files. That makes support for other languages a natural extension point, through new
agent definitions, skills and workflows, rather than a rewrite. It is not yet a proven capability.

## Where to go next

- [Quickstart](../getting-started/quickstart.md)
- [Architecture](architecture.md)
- [Agents and workflows](../workflows/agents-and-workflows.md)
- [Policy enforcement](../governance/policy-enforcement.md)
- [Known limitations](../operations/known-limitations.md)
