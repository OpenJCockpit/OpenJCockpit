# @openjcockpit/ui

Design primitives shared by the app and feature libraries: `AppShell`/`AppHeader`, `Backdrop`, `BrandLogo`, `Button`, `DataTable`, `FileImportButton`, `FormFields` (`TextField`, `TextAreaField`, `SecretField`, `SelectField`, `CheckboxField`), `IconBox`, `ModalShell`, `Panel`, `StatusChip`, `StatusMessage`, `Tabs`, `Tooltip`, form/page/feedback/dropdown class helpers, the spec list styles and `cx`.

- **Tag:** `type:ui`. May depend only on `type:util` libraries (enforced by `@nx/enforce-module-boundaries`).
- **Exports:** everything through `src/index.ts` (`import { Button } from '@openjcockpit/ui'`). Never deep-import `src/...`.
- **Styling:** each primitive owns a `*.module.scss`; global design tokens, reset and animations live in `src/styles` and are loaded once by the app with `import '@openjcockpit/ui/styles.scss'`. The primitives use these tokens (`var(--…)`) and do not render correctly without that import.
- **Tests / checks:** `nx run ui:test`, `ui:lint`, `ui:typecheck`, `ui:format-check`.
