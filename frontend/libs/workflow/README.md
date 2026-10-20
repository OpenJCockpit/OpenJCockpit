# @openjcockpit/workflow

Workflow feature: overview, design, execution (agent sequencer and log console), execution history, configuration, and the approval and prompt dialogs.

- **Exports:** `WorkflowDashboard`, `WorkflowExecution`, `WorkflowPromptDialog`.
- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries, never on another feature (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the shell keeps `FlowPanel` and `useRunLauncher`, which compose run start across workflow, spec init and preflight; `FlowPanel` renders `WorkflowExecution` from this library.
- **Tests / checks:** `nx run workflow:test`, `workflow:lint`, `workflow:typecheck`, `workflow:format-check`.
