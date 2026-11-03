# Frontend guidelines

Working rules for `frontend/`. Design background is in [frontend-architecture.md](frontend-architecture.md). Rules marked **(enforced)** fail lint or CI; the rest are review expectations.

## Structure and dependencies

- Put code in the lowest layer that can own it: `util` → `data`/`ui` → `contract` → `feature` → `app`/`remote`. A lib may only depend on layers below it **(enforced)**.
- A new lib gets a tag in `project.json` (`type:feature`, `type:ui`, `type:data`, `type:util`, `type:contract`) and an alias in `tsconfig.base.json`.
- Features do not import other features. If two need the same thing, move it down a layer or into the contract.
- Remotes contain only an entry that re-exports a feature lib's `AppDefinition` plus config. No screens.

## Federated apps

- Read host state with `useHubSession`, `useHubProject`, `useHubHeader`, `useHubTheme`. Never take it as props.
- Never import `keycloak-js` or `@openjcockpit/auth` in a remote **(enforced)**.
- Gate pages with `<RequirePermission any={[...]}>`; wrap project-scoped pages in `<RequireProject>`.
- Never add a private copy of a shared singleton. Add new shared libs in `module-federation.shared.ts` and `check-remotes.mjs` together.
- The contract is additive. Do not rename or remove fields; bump `HUB_CONTRACT_VERSION` only when the host must understand the change.
- Settings sections are content only: no `AppShell`, header, back button or `<h1>`. Wrap the default export in the remote's translation provider.

## TypeScript

- `strict` and `noUncheckedIndexedAccess` are on. Handle `undefined` from indexing instead of asserting.
- No floating or misused promises, no unnecessary assertions **(enforced)**. Handle or `void` every promise on purpose.
- No `console.log` **(enforced)**; `console.warn` and `console.error` are allowed.
- Unused variables and arguments start with `_` to be exempt **(enforced)**.
- Every `eslint-disable` needs a justification comment, and a stale directive is an error **(enforced)**. Prefer fixing the code.
- Use `curly` braces once a body leaves the header line **(enforced)**.
- Do not edit `src/generated/**`. Change `shared/contracts/openapi.yaml`, run `nx run api:generate` and commit the result. Mark always-present fields `required` in the contract.

## Data fetching

- All HTTP goes through `apiRequest` / `apiRequestVoid`. No raw `fetch` in `libs/api` outside `core/http.ts` **(enforced)**, and none in features.
- `libs/api` is pure: no React, no components, no `localStorage`.
- Server state belongs in react-query, not in `useState` + `useEffect`. Keep local state for UI-only concerns.
- Pass the `AbortSignal` from the query function or effect to the client.
- Do not retry `ApiError`; the shared query client already retries only network errors once. Do not override that per query without a reason.
- Show an empty result and a failed request differently. The clients throw so callers can tell them apart.
- Never ship mock data as a production fallback. Fixtures live in `@openjcockpit/api/testing`.

## Components and styling

- Use components from `@openjcockpit/ui` before writing new ones. Add a reusable component there with its `*.module.scss`, a test, and an export in `index.ts`.
- Style with CSS modules and design tokens (`var(--…)` from `_tokens.scss`). No hard-coded colours, no inline `style` **(enforced)**. A value computed at runtime goes through a CSS custom property, or an `eslint-disable` with a reason.
- After editing a `.module.scss`, run `npm run typings` so the `.d.ts` stays in step.
- Style for both themes. Do not branch on theme in JS; the host sets `data-theme`.
- Choose containers per [ui-containers.md](ui-containers.md): inline form for records, modal for blocking prompts, `ConfirmActionDialog` for destructive actions.
- Wrap independently failing regions in `ErrorBoundary` with translated `message` and `reloadLabel`, and `resetKeys` such as the route.
- Destructive buttons use `variant="danger"`; the dialog names the item and says what is lost.

## Accessibility

- `jsx-a11y` rules are errors **(enforced)**. Do not disable them to silence a real issue.
- Interactive elements are real buttons and links, reachable by keyboard, with a visible focus state.
- Every icon-only control has a translated `aria-label`.
- Use `ModalShell` for dialogs so focus trap, Escape and focus restore come for free.
- Do not convey state by colour alone; pair it with text or an icon.
- Add an axe check to the test for any new page or complex widget.

## Internationalisation

- User-visible text goes through `t()`. Hard-coded JSX text and `aria-label`, `placeholder`, `title`, `alt` literals warn today and will fail later **(warn)**.
- Keys are `feature.component.element`, literal or static prefix plus template, so `check:i18n` can trace them.
- Interpolate (`t('queue.count', { count })`), never concatenate. Plurals use `_one` / `_other`.
- Do not translate enum values sent to or from the API; map them to keys in the presentation layer.
- Format dates and numbers with `@openjcockpit/i18n` helpers, not `toLocaleString()`.
- Add the key to `en.json` and every other locale in the same change. Check layouts with the `en-XA` pseudo-locale in dev.

## Testing

- Every logic change or bug fix comes with a test. Prefer Testing Library queries by role and label over test ids.
- Run what you touched, then the gate:

```bash
cd frontend
npx nx run <project>:test
npm run check          # lint, typecheck, test, format-check, check-styles, check-i18n
```

- Use the helpers in `apps/dashboard/src/testUtils` (`render`, `renderRoutedApp`, fixtures, remote stubs) instead of rebuilding providers.
- Mock the network at the api boundary with `@openjcockpit/api/testing` fixtures; do not stub `fetch` ad hoc.
- Playwright (`frontend/e2e`) is for end-to-end paths against a running stack. Do not claim it covers agent pipelines.
- Timing-sensitive tests: use fake timers or `waitFor`, never fixed sleeps.

## Local development

- UI work without the backend: `npm run start:hub:mock` (signed in as `dev` with the admin role).
- Full hub against a real backend: `npm run start:hub`.
- Node 22 (see `frontend/.nvmrc`).

## Formatting and scope

- Prettier owns formatting. Format only the files you changed; do not let a bulk `--fix` touch unrelated files.
- Keep a change to one concern. Unrelated refactors go in their own PR.
- Never commit secrets, tokens or client secrets in code, config or the bundle.

## Before opening a PR

1. `npm run check` passes (or `npx nx affected -t lint typecheck test build` for a scoped change).
2. New strings exist in every locale; `npm run check:i18n` is green.
3. For a remote or contract change: `nx run-many -t build -p remote-*` then `node scripts/check-remotes.mjs`.
4. Both themes and keyboard navigation checked for UI changes.
5. PR description follows `.github/pull_request_template.md`.
