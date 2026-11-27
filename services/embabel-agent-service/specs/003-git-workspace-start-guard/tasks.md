# Tasks: Git-workspace start guard

## Spec reference

- Workflow ID: WF-003
- Spec ID: SPEC-003
- Feature folder: specs/003-git-workspace-start-guard

## Task list

Each task must reference at least one requirement or acceptance criterion.

- [x] TASK-001: Add `requiresGitWorkspace(WorkflowDefinition)` null-safe `git_*` prefix helper.
  - References: REQ-001, NFR-007
  - Validation: unit test / compile
- [x] TASK-002: Return a `BLOCKED` response in `doStart` when `gitWorkspace == null && requiresGitWorkspace`.
  - References: REQ-001, REQ-002, REQ-003
  - Validation: AC-001 test
- [x] TASK-003: Add git-tool workflow fixture and block/bind tests.
  - References: REQ-004, REQ-005
  - Validation: AC-002, AC-003, AC-004 tests

## Test tasks

- [x] TEST-001: git workflow without ticket → BLOCKED, orchestrator never called.
  - References: AC-001
  - Command: `WorkflowExecutionServiceGitWorkspaceTest#aGitWorkflowWithoutATicketIsBlockedBeforeTheOrchestratorRuns`
- [x] TEST-002: git workflow with valid ticket → bound and started.
  - References: AC-002
  - Command: `WorkflowExecutionServiceGitWorkspaceTest#aTicketBindsAGitWorkflow`
- [x] TEST-003: non-git workflow without ticket → still unbound (regression).
  - References: AC-003
  - Command: `WorkflowExecutionServiceGitWorkspaceTest#aStartWithoutTicketIsUnbound`

## Documentation tasks

- [x] DOC-001: Add this feature folder and `_index.md` row.

## Review tasks

- [ ] REVIEW-001: Validate dependency mapping
- [ ] REVIEW-002: Validate LLM strategy against data profile
- [ ] REVIEW-003: Validate all acceptance criteria
