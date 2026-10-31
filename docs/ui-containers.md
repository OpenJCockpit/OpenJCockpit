# Containers for forms, confirmations and settings

Which container to use for which task. This settles finding 23 of `ux-inconsistencies.md`. It describes the pattern the code already mostly follows, so most pages need no change.

## Rule

| Task | Container | Component |
| --- | --- | --- |
| Create or edit a persistent record with several fields (project, marketplace, A2A service, workflow, group, skill, subagent) | Inline form above the list or table | `formClass`, `EditorForm`, shared form fields |
| Confirm a destructive or irreversible action | Modal | `useConfirmDialog` / `ConfirmActionDialog` |
| A short decision or one-field prompt that blocks the current flow (add to queue, approval, comment, workflow prompt) | Modal | `ModalShell` |
| Read-only detail of a record | Inline expansion or its own page, never a modal | |
| A few toggles that belong to one panel (queue auto-merge) | In the panel | `CheckboxField` |
| Settings for an entity that spans projects (projects, marketplaces) | A page inside the sidebar shell | `pageClass`, `pageHeaderClass`, host header |
| Settings that belong to one workflow | A tab of the Workflow page | `TabNav` |

## Why

- **Inline for records.** The user sees the list while editing, and the form can hold many fields without a scroll-inside-scroll modal.
- **Modal for interruptions.** A modal is right when the user must answer before continuing. `ModalShell` already provides the focus trap, Escape, focus restore and inert background, so every blocking prompt gets accessibility for free.
- **Settings pages stay in the shell.** Settings pages render the host sidebar, so the user never loses navigation. They are not drawers or standalone top-bar pages.

## Conventions

- One inline form visible at a time per page. Opening a second one closes the first.
- A modal never contains a form with more than a few fields. If it needs more, make it an inline form.
- Destructive buttons use `variant="danger"`; the confirm dialog says what will be lost and names the item.
- Dismiss buttons in a dialog say "Cancel", except when "Cancel" would clash with the action being confirmed (a "Cancel run?" dialog). Use "Keep" there.
- The back button, where one is rendered, names its destination ("← Back to dashboard"). Pages in the sidebar shell have no back button.

## Known gaps

- Project settings can still show the project form, the credentials form and the list at once. It should show one form at a time.
- A2A services use an inline two-step confirm instead of `useConfirmDialog`. If it stays, it needs focus management when the buttons swap.
