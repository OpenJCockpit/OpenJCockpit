# @openjcockpit/spec-queue

Spec queue dashboard: queue list, item actions, auto-merge setting and the "add to queue" dialog.

- **Exports:** `SpecQueueDashboard`, `AddToQueueDialog`.
- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries, never on another feature (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the shell composes `AddToQueueDialog` into other views (e.g. spec files); this library calls the `@openjcockpit/api` spec-queue and workflow clients directly.
- **Tests / checks:** `nx run spec-queue:test`, `spec-queue:lint`, `spec-queue:typecheck`, `spec-queue:format-check`.
