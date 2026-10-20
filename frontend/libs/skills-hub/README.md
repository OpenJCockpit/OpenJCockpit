# @openjcockpit/skills-hub

Skills hub dashboard and skills marketplace settings.

- **Exports:** `SkillsHubDashboard`, `SkillsHubSettings`.
- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries, never on another feature (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the shell owns navigation between the hub and its settings (`onBack`, `onSettings`); `SkillsHubSettings` calls the `@openjcockpit/api` skills-marketplace clients directly.
- **Tests / checks:** `nx run skills-hub:test`, `skills-hub:lint`, `skills-hub:typecheck`, `skills-hub:format-check`.
