---
workflow_id: WF-001
spec_id: SPEC-001
feature_slug: example-feature
title: Example Feature
status: draft
owner: unknown
created_at: YYYY-MM-DD
updated_at: YYYY-MM-DD

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
  # Added for guardrail scanning
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

# Feature Spec: {{feature_name}}

## 1. Context

### Problem

Describe the problem this feature solves.

### Goal

Describe the intended outcome.

### Non-goals

- List what is explicitly out of scope.
- List what should not be built as part of this feature.
- List assumptions that must not silently become scope.

## 2. Users and scenarios

### Primary user

- User type:
- Main need:
- Expected outcome:

### Secondary users

- User type:
- Main need:
- Expected outcome:

### Scenarios

#### Scenario 1: {{scenario_name}}

Given ...
When ...
Then ...

#### Scenario 2: {{scenario_name}}

Given ...
When ...
Then ...

#### Scenario 3: Error or fallback scenario

Given ...
When ...
Then ...

## 3. Requirements

Use stable IDs so the agent, tests, implementation plan, and review can reference them.

### Functional requirements

- **REQ-001**: The system must ...
- **REQ-002**: The user must be able to ...
- **REQ-003**: The system must not ...

### Non-functional requirements (ISO 25010 Framework)

*Map your NFRs directly to the software quality sub-characteristics below, embedding Clean Code principles into their criteria.*

- **NFR-001 Functional Suitability**: (Appropriateness, completeness, and correctness of features)
- **NFR-002 Performance Efficiency**: (Time behavior, resource utilization, and capacity constraints)
- **NFR-003 Compatibility**: (Co-existence and interoperability with existing domain layers)
- **NFR-004 Usability / Accessibility**: (Appropriateness recognizability, learnability, and operability)
- **NFR-005 Reliability**: (Maturity, availability, fault tolerance, and recoverability)
- **NFR-006 Security Guardrails**: (Confidentiality, integrity, non-repudiation, accountability, authenticity. e.g., input scrubbing, zero-trust token verification)
- **NFR-007 Maintainability / Clean Code**: (Modularity, reusability, analyzability, changeability, testability. Define metrics like cyclomatic complexity constraints)
- **NFR-008 Portability**: (Adaptability, installability, replaceability)

## 4. Architectural Boundaries (Clean Architecture)

Define how this feature aligns with Clean Architecture separation of concerns to prevent domain leaks.

### 4.1 Domain Entities Layer
- Crucial business rules, calculations, and foundational enterprise models.
- **Rule**: Must have zero external dependencies (no frameworks, no DB references, no HTTP libraries).

### 4.2 Use Case / Application Layer
- Feature-specific application logic and business rules orchestration.
- **Rule**: Interacts with external systems strictly via Interfaces / Ports. No direct infrastructure calls.

### 4.3 Interface Adapters & Infrastructure Layer
- Controllers, Presenters, Gateways, Gatekeepers, Database repositories, and API clients.
- **Rule**: Data crossing boundaries into the Use Case layer must use clean Data Transfer Objects (DTOs), never raw database entities or HTTP payloads.

## 5. Acceptance criteria

Each acceptance criterion must be testable and should reference one or more requirements.

- **AC-001**: Given ..., when ..., then ...
  - References: REQ-001, NFR-007 (e.g., must be fully covered by an isolated domain unit test)
- **AC-002**: Given ..., when ..., then ...
  - References: REQ-002
- **AC-003**: Error scenario: given ..., when ..., then ...
  - References: REQ-003, NFR-006 (e.g., secure error message that does not leak stack traces or system state)

## 6. Edge cases

- Empty input:
- Invalid input:
- Missing permissions:
- Duplicate action:
- Timeout or unavailable dependency:
- Partial or inconsistent data:
- Large input or high-volume scenario:
- Concurrent usage:
- Unexpected external API response:
- User cancels or abandons the flow:

## 7. Data and integrations

### Input

- Source:
- Format:
- Validation rules: (Specify white-list validation strategy to satisfy NFR-006 Security)
- Contains personal data:
- Contains secrets or credentials:

### Output

- Format:
- Storage location:
- Display location:
- Retention:
- Export behavior:

### Data entities

| Entity | Description | Data classification | Source | Stored where | Clean Code Boundary (Domain / Infra) |
|---|---|---|---|---|---|
| ... | ... | ... | ... | ... | ... |

### Integrations

- APIs:
- Databases:
- External services:
- Message queues/events:
- Feature flags:
- Authentication/authorization dependencies: (State which authorization policies guard this integration)

## 8. Dependency mapping

Use this section to make dependencies explicit. Do not rely only on prose.

### Depends on

List specs that must exist or be completed before this feature can be implemented.

```yaml
depends_on:
  - spec_id: SPEC-000
    workflow_id: WF-000
    relationship: requires
    reason: "Authentication must exist before this feature can identify users."
```
