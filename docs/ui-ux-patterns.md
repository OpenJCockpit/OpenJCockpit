# UI/UX patterns

How screens in the dashboard are put together and how they behave. Components named here come from `@openjcockpit/ui` (`frontend/libs/ui`). Rules for code are in [frontend-guidelines.md](frontend-guidelines.md); which container to use for a form or confirmation is in [ui-containers.md](ui-containers.md) and is not repeated here.

## Principles

1. **One way to do each thing.** Status, buttons, empty and error states, and confirmations each have one component. Use it instead of a lookalike.
2. **The user always knows where they are and what is happening.** Every async view shows loading, empty, error and success distinctly.
3. **Say what will happen, name the thing.** Labels are verbs ("Add to queue"), dialogs name the item they affect.
4. **Governed by default.** This product puts a human in control of AI work. Anything that approves, merges, runs or deletes is explicit, reversible where possible, and never a side effect of another click.
5. **Accessible and themeable by construction.** Use the shared components and tokens and keyboard, focus, contrast and both themes come with them.

## Page anatomy

```
AppShell (backdrop + chrome)
├─ host header + sidebar (project switcher, nav, user menu)
└─ page
   ├─ page header: Eyebrow (small muted label) + title + primary action
   ├─ TabNav (only if the page has sub-views)
   └─ content: Panels (PanelHeader, PanelTitle), DataTable, inline forms
```

- Every page renders inside the host shell, so navigation is never lost. Remotes render the host header from `useHubHeader()` and do not draw their own.
- Pages that need a project are wrapped in `RequireProject`, which sends the user to the projects page and back.
- Sub-views of one thing use `TabNav` / `Tab` (for example the tabs of the Workflow page). Different things get different routes in the sidebar, not tabs.
- Cards are `Panel`. Do not nest a `Panel` inside a `Panel`; use a divider (`PanelHeader simple`).
- A page has one `<h1>`. Settings sections contributed by remotes have none, because the host owns the heading.
- Back buttons exist only on sub-views outside the sidebar shell and name their destination ("← Back to dashboard").

## Actions

| Need | Use |
| --- | --- |
| The one main action of a view or form | `Button variant="primary"`. At most one per view. |
| Everything else (Cancel, Refresh, Export, Edit) | `variant="secondary"` |
| Destructive or irreversible | `variant="danger"`, behind `useConfirmDialog` |
| Navigation that looks like a button | `buttonClass(...)` on an `<a>` or router `Link` |
| Inline text action | `variant="link"` |
| Toolbar density | `size="sm"` |

- Labels are specific verbs, translated, sentence case. "Delete project", not "OK".
- Row actions sit together in `Actions` (a `<td>` or a `<div>`), in a consistent order: primary, edit, destructive last.
- While an action runs, disable its button and show a busy label (the confirm dialog takes `busyLabel`). Never allow a double submit.
- A destructive action says what is lost and names the item. Its dismiss button says "Cancel", or "Keep" when "Cancel" would clash with the action ("Cancel run?").
- Prefer undo-free safety over undo: confirm first, then do it.

## The four states of every async view

Every view that loads data renders all four, with these components:

| State | Component | Behaviour |
| --- | --- | --- |
| Loading | `LoadingState` (inline), `StatusMessage variant="loading"` (whole page) | Specific copy ("Loading the queue…"), `role="status"`, use the shared `ELLIPSIS` character. |
| Empty | `EmptyState` | Title says what is missing, `hint` says why, `action` offers the next step. |
| Error | `ErrorState` | `role="alert"`, a human message, and `onRetry` with a translated `retryLabel` when retrying makes sense. |
| Content | the view | |

Rules:

- **Empty is not failed.** The API clients throw on failure, so an empty list means empty. Never render an error as "nothing here".
- Do not flash a spinner for fast loads when stale data exists; react-query keeps the previous data (`staleTime` 30 s), so show it and refresh quietly.
- Full-page notices (403, 404, crash) use `StatusMessage variant="page"` with an action that leads somewhere.
- A section that can fail independently sits in its own `ErrorBoundary`, so one broken widget does not blank the page.

## Forms and validation

- Use the field components (`TextField`, `TextAreaField`, `SelectField`, `CheckboxField`, `SecretField`). They wire the label, hint, error id and `aria-invalid` for you.
- Layout: `formClass` with `formGridClass`; `wide` for fields that span the grid; Save and Cancel in `formActionsClass`, primary on the action that saves.
- Required fields are marked with `required`. Validate on blur or submit, not on every keystroke. Show the message under the field (`error`), and keep the user's input.
- Hints (`hint`) explain format and consequences up front; errors say how to fix it.
- Server errors that cannot be tied to a field appear as an `ErrorState` or `fieldErrorClass` line above the actions, using the translated message for the server's error code.
- Secrets use `SecretField`: masked by default, never echoed back from the server, never logged.
- One inline form open at a time per page. Opening another closes the first.
- Leaving a form with unsaved changes asks first only when the loss would be costly (long text, many fields). Short forms do not nag.

## Status and feedback

- **Status is a chip.** Project, workflow, queue and Skills Hub state all render through `StatusChip` with a `StatusKind`: `active`, `done`, `waiting`, `open`, `review`, `changes`, `ok`, `neutral`, `error`. Map domain values to a kind in the presentation layer; add a new kind (with its style) rather than a one-off colour.
- The chip always carries a text label. Colour reinforces it and is never the only signal.
- **Inline feedback** after an action uses `StatusMessage` (`info` or `error`) or `feedbackResultClass` for a result with a follow-up link ("Spec saved. Open it").
- Success is quiet and local: put the confirmation next to what changed. Errors are loud (`role="alert"`) and stay until dismissed or fixed.
- Use `Tooltip` for short supplementary labels only (icon-only buttons, truncated text). Never put essential information or interactive content in a tooltip; it must also be reachable by keyboard focus.

## Tables and lists

- Use `DataTable` for records with several attributes. It scrolls horizontally inside its wrapper so it never widens the page. `DataTableRow inactive` dims deactivated records.
- Use a list (such as the spec list styles) for items that are mainly a name plus a status.
- Put the identifying column first and actions in the last column. Keep the status as a `StatusChip`.
- Long text truncates with a tooltip or expands inline; it does not break the layout.
- Read-only detail of a row expands inline or opens its own page, never a modal.

## Human-in-the-loop patterns

The product's core is governed agent work. These behaviours are deliberate:

- **Approval gates are blocking and explicit.** A gate is a modal (`ModalShell`) with the decision, the context needed to make it, and clearly labelled approve and reject actions. It never auto-dismisses.
- **Show what the agent will do and did.** Runs show their status as a `StatusChip`, the agent sequence and the result with a link to the reviewable Git change.
- **Permissions shape the UI.** Use `Can` / `useCan` to hide controls the user may not use and `RequirePermission` to answer a forbidden route with the 403 page. Never rely on hiding alone; the backend enforces authorisation.
- **Risky settings are separated.** Queue auto-merge and similar toggles sit in their own panel with a hint that states the consequence.

## Navigation and routing

- The sidebar entries come from the registered apps (`order`, `showInNav`) and show badge counts through `useBadgeFor`.
- Settings live under `/settings/<id>` and `/project-settings/<id>`; sections are ordered by band (Workspace, Integrations, Project integrations) and grouped by `groupKey`.
- The selected project persists across pages. A page that needs one never silently picks one for the user.
- Links go to routes; buttons perform actions. Do not use a `<div onClick>`.
- A remote that is unavailable is skipped, and the host shows a notice listing what is missing instead of failing the page.

## Visual language

- Dark "cockpit" look by default with a light theme; the host sets `data-theme`. All colours come from tokens in `libs/ui/src/styles/_tokens.scss`, including semantic aliases (`--status-ok`, `--status-warn`, `--status-error`).
- Spacing uses the 4 px scale (`--space-1` … `--space-8`), radii `--radius-sm|md|lg`, text sizes `--text-xs … --text-lg`. Do not invent values.
- Surfaces are glass `Panel`s over the `Backdrop`; elevation uses `--shadow` and z-index uses `--z-raised` and `--z-overlay`.
- Accent is cyan, success is teal, warning is amber, error is red. Reserve red for failure and destruction.
- Decorative frames and backgrounds (`FrameOctagon`, `PuffsBackground`) are for the landing hub and shell, not for content panels.

## Motion

- Motion is entrance and feedback only, built with `Reveal` / `RevealGroup`: CSS-only, with explicit `index` for staggering.
- Reduced motion is honoured globally in `_reset.scss`. Use `usePrefersReducedMotion` for any motion done in JS, and never make information depend on an animation.
- Keep durations short and do not animate on every data refresh.

## Accessibility behaviours

- Dialogs use `ModalShell` (focus trap, Escape, focus restore, inert background) and are named through `labelledBy`.
- Loading is announced (`role="status"`, `aria-live="polite"`), failures are announced (`role="alert"`).
- Every control has an accessible name; icon-only buttons get a translated `aria-label`.
- Tab order follows visual order. Focus moves into a dialog on open and back to its trigger on close. After deleting an item, move focus to a sensible neighbour.
- Text and controls keep contrast in both themes. Check any new colour in the light theme.

## Content and microcopy

- Sentence case, plain words, no jargon the user did not choose. Use the product's terms consistently: project, spec, workflow, agent, run, queue, approval gate.
- Say what happened and what to do next. Error messages explain the cause and the fix; do not show raw status codes or stack traces.
- Loading copy is specific ("Loading the queue…"), empty copy explains why it is empty, and button copy is a verb.
- Numbers, dates and plurals go through the i18n helpers and `_one` / `_other` keys. Allow text to grow: layouts must survive the `en-XA` pseudo-locale.
- Backend, docs and agent or workflow output are not translated.

## Adding a new screen: checklist

1. Which container does each interaction need? ([ui-containers.md](ui-containers.md))
2. Loading, empty, error and content states all present, with shared components.
3. One primary action; destructive actions confirmed and named.
4. Status shown through `StatusChip`, with text.
5. Permissions: `RequirePermission` on the route, `Can` on controls.
6. Keyboard-only walkthrough, in both themes, and in `en-XA`.
7. Strings in every locale; an axe check in the test.

## Known gaps

- Project settings can show the project form, the credentials form and the list at once; it should show one form at a time.
- A2A services use an inline two-step confirm instead of `useConfirmDialog`, and need focus management when the buttons swap.
- There is no global toast or notification component. Feedback is inline today; add one component rather than ad hoc banners if the need arises.
