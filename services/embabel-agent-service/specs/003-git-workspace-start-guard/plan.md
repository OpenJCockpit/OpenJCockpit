# Implementation Plan: Git-workspace start guard

## Spec reference

- Workflow ID: WF-003
- Spec ID: SPEC-003
- Feature folder: specs/003-git-workspace-start-guard

## Summary

Add a fail-fast guard in `WorkflowExecutionService.doStart` so a workflow that declares any
`git_*` MCP tool cannot start unless a git workspace is bound to the run (via a redeemed
git-workspace ticket). Non-git workflows and valid-ticket flows are unchanged.

## Architecture impact

No new layers, endpoints, or persistence. The guard is a pure rule applied inside the existing
start use case, between ticket redemption and the orchestrator hand-off.

## Files expected to change

- `src/main/java/nl/metafactory/agents/workflow/WorkflowExecutionService.java`
  - add `requiresGitWorkspace(WorkflowDefinition)` helper;
  - return a `BLOCKED` `WorkflowStartResponse` when `gitWorkspace == null && requiresGitWorkspace`.
- `src/test/java/nl/metafactory/agents/workflow/WorkflowExecutionServiceGitWorkspaceTest.java`
  - add a git-tool workflow fixture and tests for block + valid-ticket bind.

## Dependency impact

None. Reuses existing `GitWorkspaceTickets` redemption.

## Data/privacy impact

None. No new data captured; no secrets logged (the message contains no ticket value).

## Security impact

Fail-closed improvement: removes the "agent hallucinates around missing git tools" failure mode.
Message leaks no ticket, credential, or stack trace.

## LLM strategy validation

Enterprise-cloud strategy with `allow_tool_use: true` is compatible; no prompt or model change.

## Implementation steps

1. Add `requiresGitWorkspace` helper (null-safe `git_*` prefix check on `mcpTools`).
2. In `doStart`, after ticket redemption, block when `gitWorkspace == null && requiresGitWorkspace`.
3. Add git-tool fixture and two tests (blocked-without-ticket; bound-with-ticket) to
   `WorkflowExecutionServiceGitWorkspaceTest`.
4. Run the module's tests.

## Risks

- Orb-child workflows that declare git tools will now block instead of starting unbound (intended).

## Rollback plan

Revert the two-file change; behaviour returns to starting unbound and failing in the runtime.

## Human approvals required

None triggered: no customer data mutation, no secrets, no external messages, no auth/logging changes.

## Ready for tasks

- [x] Yes
- [ ] No
