---
workflow_id: WF-003
spec_id: SPEC-003
feature_slug: git-workspace-start-guard
title: Git-workspace start guard
status: draft
owner: unknown
created_at: 2026-11-26
updated_at: 2026-11-26

lifecycle:
  phase: specify
  allowed_next_phases:
    - clarify
    - plan
    - tasks
    - implement
    - validate

dependencies:
  blocks: []
  blocked_by: []
  depends_on: []
  related: []
  supersedes: []
  superseded_by: []

data_profile:
  data_classification: internal
  contains_personal_data: false
  contains_special_category_data: false
  contains_secrets: false
  avg_relevant: false
  lawful_basis: unknown
  dpia_required: unknown
  data_residency: unknown
  retention_policy: unknown
  allowed_data_sources: []
  prohibited_data_sources:
    - credentials
    - secrets
    - production_tokens

llm_strategy:
  mode: enterprise_cloud
  allowed_model_classes:
    - enterprise_cloud
  prohibited_model_classes:
    - public_consumer_llm
  prompt_data_policy: minimize_sensitive_data
  allow_training_on_data: false
  allow_rag: true
  allow_tool_use: true
  human_approval_required:
    - external_message_send
    - customer_data_export
    - deletion_or_mutation_of_customer_data
  logging_policy: redact_sensitive_data
  fallback_strategy: human_review

risk_profile:
  business_impact: low
  privacy_risk: low
  security_risk: low
  user_impact: low
  requires_security_review: false
  requires_privacy_review: false
  requires_legal_review: false
  architecture_pattern: clean_architecture
  compliance_targets: [iso25010, owasp_top_10]

validation:
  required_checks:
    - requirements_traceability
    - acceptance_criteria_coverage
    - dependency_check
    - test_coverage
    - static_security_analysis
    - clean_architecture_boundary_check
---

# Feature Spec: Git-workspace start guard

## 1. Context

### Problem

A workflow that declares git MCP tools (`git_*`) can only run meaningfully against a repository
bound to the run as a git workspace. When such a workflow is started without a git-workspace
ticket — for example from a start path that omits the selected project, from the spec queue, via
A2A, or as a workflow-orb child — the run starts unbound and the runtime refuses its git tools
with `No git workspace is bound to this run: git tools are only available when the workflow runs
on a project (ai-control prepares the workspace).` By then the agent has already begun
hallucinating around the unavailable tools (e.g. "I will skip creating the branch").

### Goal

Fail fast: a start of a git-tool workflow with no bound git workspace is blocked before any agent
runs, with an actionable message telling the operator to select a project with a git repository
and start from the project workspace.

### Non-goals

- Preparing the git workspace itself (ai-control's `ProjectWorkflowRunService` already does that).
- Issuing or redeeming git-workspace tickets (existing `GitWorkspaceTickets` behaviour is unchanged).
- Changing how non-git workflows start without a ticket (they may still run as plain/unbound).
- Frontend changes (covered separately in the dashboard's Workflow overview start fix).

## 2. Users and scenarios

### Primary user

- User type: dashboard operator starting a workflow.
- Main need: a clear, immediate error when a git-requiring workflow cannot be bound.
- Expected outcome: the start is blocked with an actionable message; no agent runs.

### Secondary users

- User type: spec-queue / A2A / workflow-orb trigger.
- Main need: a git-tool workflow that cannot be bound must not silently start unbound.
- Expected outcome: the same BLOCKED response.

### Scenarios

#### Scenario 1: git workflow started without a ticket

Given a workflow that declares `git_*` tools and no git-workspace ticket on the start input,
When `WorkflowExecutionService.doStart` runs,
Then the start returns `BLOCKED` with a message containing "requires a git workspace" and the
orchestrator is never invoked.

#### Scenario 2: git workflow started with a valid ticket

Given the same workflow and a valid ticket issued for this workflow and caller,
When the start runs,
Then the ticket is redeemed, the run is bound to the workspace, and the orchestrator starts the
run with that workspace.

#### Scenario 3: non-git workflow without a ticket

Given a workflow with no `git_*` tools and no ticket,
When the start runs,
Then it starts unbound as before (no behaviour change).

## 3. Requirements

### Functional requirements

- **REQ-001**: The system must block a start when the workflow declares any `git_*` MCP tool and
  no git workspace is bound to the run.
- **REQ-002**: The block must be returned as a `WorkflowStartResponse` with status `BLOCKED` and a
  message that names the workflow and tells the operator to select a project with a git repository.
- **REQ-003**: The block must occur before the orchestrator starts the run (no agent may run).
- **REQ-004**: The system must not change behaviour for workflows with no `git_*` tools.
- **REQ-005**: An invalid/expired ticket must keep its existing dedicated message and must not be
  masked by this guard.

### Non-functional requirements (ISO 25010 Framework)

- **NFR-001 Functional Suitability**: the guard blocks exactly the unbound git-tool start case.
- **NFR-003 Compatibility**: existing valid-ticket and non-git flows are unchanged.
- **NFR-005 Reliability**: fail-closed; no path starts a git workflow unbound.
- **NFR-006 Security Guardrails**: the message leaks no ticket, credential, or stack trace.
- **NFR-007 Maintainability / Clean Code**: detection is a small, testable pure helper.

## 4. Architectural Boundaries (Clean Architecture)

### 4.1 Domain Entities Layer
- `WorkflowDefinition` and its `mcpTools` are domain data; the `git_*` detection is a pure rule
  over that data with no framework or infrastructure dependency.

### 4.2 Use Case / Application Layer
- `WorkflowExecutionService.doStart` orchestrates the start and applies the guard before handing
  off to the orchestrator.

### 4.3 Interface Adapters & Infrastructure Layer
- The `WorkflowStartResponse` DTO carries the BLOCKED result to the caller (ai-control / dashboard).

## 5. Acceptance criteria

- **AC-001**: Given a git-tool workflow and no ticket, when the start runs, then the response is
  `BLOCKED` and the orchestrator is never called. References: REQ-001, REQ-003.
- **AC-002**: Given a git-tool workflow and a valid ticket, when the start runs, then the run is
  bound and started. References: REQ-001.
- **AC-003**: Given a non-git workflow and no ticket, when the start runs, then it starts unbound.
  References: REQ-004.
- **AC-004**: Given an invalid ticket, when the start runs, then the existing invalid-ticket message
  is returned (not the git-workspace guard message). References: REQ-005.

## 6. Edge cases

- Workflow with `mcpTools == null` or empty: treated as non-git (no block).
- Tool entry with a null `name`: ignored by the detection.
- Invalid ticket: existing invalid-ticket block wins.
- Workflow-orb child with `git_*` tools and no ticket: blocked (intended fail-fast).

## 7. Data and integrations

### Input
- Source: workflow definition (`mcpTools`) and start input (`gitWorkspaceTicket`).
- Format: in-memory records.
- Validation rules: `git_*` prefix match on tool names; ticket redemption via existing service.
- Contains personal data: no.
- Contains secrets or credentials: no (tickets are never logged).

### Output
- Format: `WorkflowStartResponse` (`BLOCKED`, message).
- Storage location: none (response only).
- Display location: dashboard start feedback.
- Retention: none.
- Export behavior: none.

### Data entities

| Entity | Description | Data classification | Source | Stored where | Clean Code Boundary |
|---|---|---|---|---|---|
| WorkflowDefinition | workflow + declared tools | internal | workflow store | in-memory | Domain |

### Integrations
- APIs: none new.
- Databases: none new.
- External services: none new.
- Authentication/authorization dependencies: existing ticket identity check (unchanged).

## 8. Dependency mapping

### Depends on

```yaml
depends_on: []
```
