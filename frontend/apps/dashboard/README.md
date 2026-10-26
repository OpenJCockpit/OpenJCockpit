# @openjcockpit/dashboard

The shell app of the OpenJCockpit frontend: React 19, Vite, React Router and TanStack Query. It owns routing, the auth session and the project guard, and composes the feature libraries under `frontend/libs/`. Node 22 (see `frontend/.nvmrc`).

## Folder map

| Path                                                                 | Purpose                                                                         |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `src/main.tsx`                                                       | Entry point: providers, router, global styles.                                  |
| `src/App.tsx`                                                        | Layout, route table and project guard. The only place that knows every feature. |
| `src/routes.ts`                                                      | Route paths (`ROUTES`) and `workflowRunPath()`.                                 |
| `src/lazyViews.ts`                                                   | Lazy-loaded feature views (one chunk per view).                                 |
| `src/queryClient.ts`                                                 | Shared TanStack Query client and its retry policy.                              |
| `src/useAuthSession.ts`                                              | Keycloak session, token hand-off to `@openjcockpit/api`, refresh.               |
| `src/useRunLauncher.ts`                                              | Starts a workflow run from the UI.                                              |
| `src/components/`                                                    | App-level components (shell, navigation).                                       |
| `src/styles/`                                                        | Global tokens, reset and animations (see Styling).                              |
| `visual/`                                                            | Visual-regression runner and the `check-styles` script.                         |
| `scripts/`                                                           | `check-bundle-size.mjs`.                                                        |
| `Dockerfile`, `nginx.conf.template`, `security-headers.inc.template` | Production image.                                                               |

## Routes

| Path          | View                                              |
| ------------- | ------------------------------------------------- |
| `/`           | Landing hub                                       |
| `/projects`   | Projects                                          |
| `/dashboard`  | Project dashboard                                 |
| `/settings`   | Project settings                                  |
| `/workflow`   | Workflows (`/workflow/:id/runs/:runId` for a run) |
| `/spec-files` | Spec files                                        |
| `/spec-queue` | Spec queue                                        |
| `/skills-hub` | Skills Hub (`/skills-hub/settings`)               |

`src/routes.ts` is authoritative.

## Data flow

Feature libraries expose query hooks and views. They call the HTTP clients in `@openjcockpit/api`, which use `apiRequest` (bearer token, abort signal, one refresh-and-retry on 401). `useAuthSession` obtains the token from Keycloak (`@openjcockpit/auth`), passes it to `setAuthToken` and registers the refresher with `setSessionRefresher`. Fixture data for tests lives in `libs/api/src/testing` (`@openjcockpit/api/testing`); there is no mock fallback in production.

Render errors are caught by `ErrorBoundary` from `@openjcockpit/ui`.

## Commands

Run from `frontend/`.

| Task                 | Command                                                                      |
| -------------------- | ---------------------------------------------------------------------------- |
| Dev server           | `npx nx dev dashboard`                                                       |
| Production build     | `npx nx build dashboard`                                                     |
| Unit tests           | `npx nx test dashboard`                                                      |
| Everything CI runs   | `npx nx run-many -t lint check-styles typecheck coverage build check-bundle` |
| Affected only        | `npx nx affected -t lint typecheck test build`                               |
| Regenerate API types | `npx nx run api:generate`                                                    |
| Visual regression    | `npx nx run dashboard:visual-baseline`, then `dashboard:visual-check`        |
| Bundle budget        | `npx nx run dashboard:check-bundle`                                          |
| Format check         | `npx nx run dashboard:format-check`                                          |

`npm run check` at the workspace root runs `lint typecheck test format-check check-styles`; it does not cover coverage, build or the bundle budget.

`lint:fix` and `format` exist as app scripts (`npm run lint:fix`, `npm run format` inside `apps/dashboard`). Run them only on files you are changing. Never run them across the whole folder.

## Styling

- Global CSS is limited to `src/styles/` (tokens, reset, animations), loaded once from `main.tsx`. Declare colors, fonts, spacing and radii as custom properties in `styles/_tokens.scss` and use `var(--…)`. `libs/ui` depends on these tokens and does not render correctly without them.
- Component styles live next to the component in `Name.module.scss` and are imported as `import styles from './Name.module.scss'`. Write class names in kebab-case or BEM; read them in TSX as camelCase (`styles.statusChipActive`).
- Shared building blocks live in `libs/ui` (`@openjcockpit/ui`: `Button`, `Panel`, `DataTable`, `Tabs`, form classes, …). Prefer them over new one-off styles.
- Static `style={{…}}` is not allowed (ESLint `no-restricted-syntax`). Use a module class, or set a custom property for a runtime value.
- `check-styles` reports module classes that are referenced but undefined (or defined but unused) and custom properties that are never declared.
- The visual targets take Playwright screenshots of every main view (auth stubbed, API mocked, no backend needed) and compare them pixel for pixel. Take a baseline before a styling change and check after it. Set `VISUAL_BUILD=1` to check a production build instead of the dev server. Screenshots are git-ignored and need the Playwright browsers installed in `../../e2e` (`npx playwright install chromium`).

## Docker image

`Dockerfile` builds the app and serves it with unprivileged nginx on port **8080** (Compose maps `4000:8080`). Configuration is injected at container start, not at build time:

- `/config.json` serves `KEYCLOAK_URL`, `KEYCLOAK_REALM` and `KEYCLOAK_CLIENT_ID` (read by `@openjcockpit/auth`).
- `/version.json` serves `BUILD_SHA`.
- `/healthz` is the liveness probe and the Docker `HEALTHCHECK`.
- `/api/` is proxied to `API_UPSTREAM`.

All variables, defaults and the CSP are described in the [configuration reference](../../../docs/readme/reference/configuration-reference.md#dashboard-container).

## Quality

- `jsx-a11y` lint, `vitest-axe` in the unit tests and `e2e/specs/axe-a11y.spec.ts` cover part of the WCAG criteria. `prefers-reduced-motion` cannot be checked automatically, so it stays a manual review point.
- Every project enforces a 100% coverage threshold.
- Editor: install the Prettier extension and enable format on save. `.editorconfig` and `.prettierrc.json` use the same values, so the editor and the command line format identically.

## Libraries

Each lib has its own README: [`api`](../../libs/api/README.md), [`auth`](../../libs/auth/README.md), [`ui`](../../libs/ui/README.md), [`workflow`](../../libs/workflow/README.md), [`project`](../../libs/project/README.md), [`spec-queue`](../../libs/spec-queue/README.md), [`spec-files`](../../libs/spec-files/README.md), [`skills-hub`](../../libs/skills-hub/README.md), [`landing-hub`](../../libs/landing-hub/README.md). Import a lib only through its package entry point, never a deep path.
