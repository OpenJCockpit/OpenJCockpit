# @openjcockpit/api

Contract layer of the dashboard: every HTTP client, DTO type and pure DTO selector.

- **Exports:** `API_BASE_URL`, `setAuthToken()`, `authHeaders()`, `errorMessageFrom()`; the DTO types; the project, spec, skills-marketplace, workspace, workflow and spec-queue clients; `workflowsForProject()`, `candidateOrbTargets()`, `runStatus*()`/`buildAgentCards()`/`buildSequencerNodes()`, `referenceLabel()`; the mock workspace and workflow fixtures used as offline fallbacks.
- **Layout:** `core/` (base URL, token, error parsing), `types/`, `projects/`, `specs/`, `skills-marketplace/`, `workspace/`, `workflow/`, `spec-queue/`; everything is re-exported from `src/index.ts`.
- **Rules:** pure code only. No React, no components, no `localStorage`. Behavior of the clients, including the mock-workspace fallback, is unchanged from the former `apps/dashboard/src/api.ts (removed)`.
- **Auth token:** `setAuthToken` holds module state, so exactly one copy of this library must exist in the bundle. The app's auth effect is the only writer.
- **Tag:** `type:data`. May depend only on `type:util` libraries (enforced by `@nx/enforce-module-boundaries`), so it must not import `@openjcockpit/ui`.
- **Tests / checks:** `nx run api:test`, `api:lint`, `api:typecheck`, `api:format-check`.
