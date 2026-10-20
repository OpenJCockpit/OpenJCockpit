# @openjcockpit/project

Project selection and project settings.

- **Exports:** `ProjectSelection`, `ProjectSettings`, `GitStatusBadge`, `getStoredProjectId()` / `setStoredProjectId()` / `clearStoredProjectId()` (browser state under the `openjcockpit_project_id` `localStorage` key).
- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries, never on another feature (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the shell owns which project is active; the components receive projects and callbacks as props and call the `@openjcockpit/api` clients only for project CRUD, git credentials and status.
- **Tests / checks:** `nx run project:test`, `project:lint`, `project:typecheck`, `project:format-check`.
