# @openjcockpit/spec-files

Spec files dashboard: spec list and editor with save-to-pull-request.

- **Exports:** `SpecFilesDashboard`, `RenderAddToQueueArgs`.
- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries, never on another feature (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the "Add to queue" action is a slot (`renderAddToQueue`); the shell composes `AddToQueueDialog` from `@openjcockpit/spec-queue` into it. Without the prop the action is hidden.
- **Tests / checks:** `nx run spec-files:test`, `spec-files:lint`, `spec-files:typecheck`, `spec-files:format-check`.
