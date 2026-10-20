# Spec: Integrate the landing app into the dashboard app (Nx workspace, feature libraries)

Status: draft (v3: adds phase 0, converting landing to dashboard conventions before the merge)
Scope: `apps/landing`, `apps/dashboard`, new root JS workspace (`nx.json`, `libs/`), `docker-compose.yml`, Keycloak realm, CI, e2e, scripts, docs

Architecture decision: **one deployed shell app, many Nx libraries.** The code is split by feature into libraries with enforced boundaries. The deployment is not split: one app, one origin, one Keycloak login. Nx manages the JS side only (`apps/*`, `libs/*`, `e2e`); the Java services stay on Maven and are not touched.

## 1. Problem

The frontend is two separate Vite/React apps:

|                           | `apps/landing`                                                                     | `apps/dashboard`                             |
| ------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------- |
| Language / styling        | JSX, global SCSS                                                                   | TSX, CSS Modules, strict tsc, ESLint, Vitest |
| Dev port / container port | 3000                                                                               | 4000                                         |
| Purpose                   | Post-login "Choose your workspace" hub (two portal orbs: Business, Implementation) | The product UI                               |
| Auth                      | Own Keycloak init (`login-required`), own token refresh                            | Own Keycloak init, own token refresh         |

Consequences:

- **Double login handshake.** Landing authenticates, then `window.location.href` navigates to `:4000`, which runs a second full Keycloak init/redirect.
- **Hardcoded cross-app URLs.** `PortalOrb.jsx` hardcodes `http://localhost:4000` and `http://localhost:4000/?view=spec-files`. This breaks in any non-local deployment.
- **Duplicated tooling and infra.** Two Dockerfiles, two nginx configs, two compose services, two ports, two CI matrix entries, two e2e Playwright projects, two lockfiles, two sets of dependencies (React, keycloak-js, vite, sass).
- **Duplicated auth code.** `auth/keycloak.js` and `auth/keycloak.ts` differ (landing reads `VITE_KEYCLOAK_*` env vars, dashboard hardcodes `localhost:8080`).
- **Quality gap.** Landing has no TypeScript, lint, tests or format checks.
- **Keycloak client** (`openjcockpit`) must whitelist both origins.

## 2. Goal

One frontend app (`apps/dashboard`) served from one origin. The landing hub becomes a view inside it, implemented as the `landing-hub` library. `apps/landing` is deleted. The frontend moves into an Nx workspace so that further applications and features can be added as libraries (or later, as separate apps) without duplicating tooling.

### Non-goals

- Redesigning the landing visuals or copy (a straight port; "NEXUS PORTAL" naming etc. is out of scope).
- Introducing a router library (see §4.2).
- Changing backend APIs.
- Renaming the dashboard package or the `apps/dashboard` directory.
- Splitting the deployment into several apps or micro-frontends. Promoting a library into its own app later must stay possible, but is not done here.
- Nx for the Maven modules.
- Extracting the existing dashboard features (queue, spec, workflow, skills hub) into libraries: planned as phase 2 (§5), not required for the landing merge.

## 3. Requirements

### Functional

- **FR-1** After login the user lands on the landing hub view (portal orbs + intro greeting), as today.
- **FR-2** The **Implementation** orb opens the existing dashboard flow (project selection, then dashboard), with no page navigation or second Keycloak init.
- **FR-3** The **Business** orb opens the `spec-files` view (what `/?view=spec-files` does today). Project selection rules for that view are unchanged.
- **FR-4** The existing `?view=spec-files` deep link keeps working and bypasses the hub.
- **FR-5** The user can get back to the hub from the dashboard (e.g. clicking the brand/logo in `SiteHeader`, or a "Hub" entry).
- **FR-6** Logout from the hub and from the dashboard both call `keycloak.logout({ redirectUri: window.location.origin })`. The hub's TopBar user popover (Profile / Notifications / Messages) is ported as is.
- **FR-7** The intro greeting plays once per browser session (decision 2), not on every return to the hub.
- **FR-8** Exactly one Keycloak initialisation and one token-refresh timer for the whole app.

### Non-functional

- **NFR-1** Landing code is converted to TSX + CSS Modules following dashboard conventions (done in place in phase 0, §4.7, before it is moved) (`*.module.scss`, camelCase locals, `src/ui/` primitives). It passes `tsc`, ESLint (including jsx-a11y) and Prettier.
- **NFR-2** Landing global styles (`styles.scss`: body background, `.app-shell`, `.ambient`, `.scanlines`, `.nav-button` etc.) must not leak into dashboard views. Everything is scoped to the hub component.
- **NFR-3** The hub is loaded with `React.lazy`/dynamic import so the canvas background (`PuffsBackground`), motion helpers and vendored react-bits code do not increase the initial bundle for users who deep-link into the dashboard.
- **NFR-4** The hub honors `prefers-reduced-motion` (the current `IntroGreeting` gap noted in `e2e/README.md` F3 should be fixed in the port, or explicitly kept and recorded).
- **NFR-5** Net dependency change: none added. `apps/landing` dependencies are a subset of the dashboard's.

## 4. Design

### 4.0 Nx workspace

- Add a repo-root `package.json` (`"private": true`, npm `workspaces`: `apps/*`, `libs/*`, `e2e`), `nx.json` and `tsconfig.base.json` (with path aliases `@openjcockpit/<lib>`). One root `package-lock.json` replaces the per-app lockfiles. Nx is a root devDependency, version pinned like the other dev tooling.
- Vite, Vitest, ESLint, Prettier, and the `check:styles` / `visual:*` scripts stay as they are and are exposed as Nx targets (`build`, `test`, `lint`, `format:check`, `check-styles`, `visual`). Prefer inferred targets from the Nx Vite/Vitest/ESLint plugins; fall back to `nx:run-commands` for the custom scripts.
- Enable caching for `build`, `test`, `lint`, `check-styles`. Local cache only (decision 6).
- Module boundaries via `@nx/enforce-module-boundaries`, with tags:
  - `type:app` may depend on `type:feature`, `type:ui`, `type:data`, `type:util`.
  - `type:feature` may depend on `type:ui`, `type:data`, `type:util`, never on another feature.
  - `type:ui` depends only on `type:util`; `type:data` (api, auth) only on `type:util`.
- Dockerfile for the dashboard builds with the repo root as context, using `npm ci` followed by `nx build dashboard` (or `nx prune` if image size matters); only `apps/dashboard/dist` is copied into the nginx stage. `docker-compose.yml` build context changes accordingly.
- `.nvmrc`/`engines` pins Node 22, matching the current Docker and CI images.

### 4.1 Code layout

```
apps/
  dashboard/                  # shell: auth gate, view switching, composition only
libs/
  auth/                       # type:data   keycloak singleton + env config, getUserFromToken, token refresh helper
  ui/                         # type:ui     design tokens + primitives (Backdrop, Panel, BrandLogo, Button, ...)
                              #             + backgrounds/PuffsBackground, frames/FrameOctagon, motion/*,
                              #             vendor/react-bits (with ATTRIBUTION.md)
  landing-hub/                # type:feature LandingHub, IntroGreeting, PortalOrb, HubTopBar (+ .module.scss, tests)
```

- In this change only `apps/dashboard`, `libs/auth`, `libs/ui` and `libs/landing-hub` are created. The existing `ui/*` primitives move from `apps/dashboard/src/ui` into `libs/ui`, and `apps/dashboard/src/auth` into `libs/auth`, as a mechanical move with no behavior change. Everything else in the dashboard stays where it is until phase 2.
- `libs/landing-hub` exports `LandingHub` and the types of its props only (`user`, `onSelectPortal(portalId)`, `onLogout`). It never imports `keycloak-js` or `libs/auth` directly; the shell passes everything in (keeps the library trivially testable and free of auth side effects).
- `libs/landing-hub` is exported lazily from the shell (`React.lazy(() => import('@openjcockpit/landing-hub'))`).
- Phase 2 targets, one library per existing feature folder: `libs/spec-queue` (`queue/`), `libs/spec-files` (`spec/`), `libs/workflow` (`workflow/`), `libs/skills-hub` (`SkillsHub*`), `libs/project` (`ProjectSelection`, `ProjectSettings`), plus `libs/api` for the shared `api.ts` / `httpErrors.ts` / `types.ts`. `App.tsx` shrinks to composition.
- Library names are kebab-case; each has its own `README.md` (what it exposes, what it may depend on).
- Before moving each primitive from `landing/src/ui`, check for an existing dashboard equivalent and reuse it where it exists (no duplicates; `Backdrop`, `Panel` and `BrandLogo` already exist). Move the rest into `libs/ui`.
- `apps/landing/src/ui/README.md` content is merged into dashboard docs (or `src/ui` README), not dropped.
- Update `NOTICE` paths for the vendored react-bits file (`apps/dashboard/src/ui/vendor/react-bits/`) and keep `ATTRIBUTION.md` with it.
- Landing `public/` assets: favicons and icons are identical in name to the dashboard's. Verify they are the same files, keep the dashboard copies, discard the duplicates. `index.html` `<title>` and icon links stay as the dashboard has them.

### 4.2 View state, not a router

The dashboard already navigates with `view` state (`AppView`) and a `?view=` deep link (`DEEP_LINK_VIEWS`). Extend that instead of adding a router:

- Add `'landing'` to `AppView`. It becomes the initial view once `authReady` (replacing `'project-selection'` as the default entry), unless a deep link is pending (FR-4).
- Orb handlers become callbacks passed from `App`:
  - Implementation → `setView('project-selection')` (or `'dashboard'` if a stored project exists, matching today's behavior after landing → dashboard).
  - Business → `setPendingView('spec-files')` then project selection / existing deep-link consumption path (`consumeDeepLink`), so the existing project-selection-first behavior is preserved.
- Back-to-hub (FR-5) is `setView('landing')`.
- No browser history entries are added (same as the other views today). Deep-linking to `?view=landing` is not required. If history support is wanted, that is a separate change.

### 4.3 Auth

- The dashboard's `App` auth effect is the single owner of Keycloak init, `setAuthToken` and the refresh timer. The hub receives `user` (derived from `tokenParsed` using landing's `getUserFromToken`, moved into `auth/keycloak.ts` or `auth/user.ts`) and an `onLogout` callback. It does not call `getKeycloak()` itself.
- Delete `apps/landing/src/auth/keycloak.js`. Make `apps/dashboard/src/auth/keycloak.ts` read `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID` with the current hardcoded values as defaults (the landing version's behavior), so the merge does not lose configurability. Add `vite-env.d.ts` typings for them.
- Both apps currently skip `StrictMode` because of the Keycloak singleton double-init. That stays out of scope; the comments in `main.tsx` stay accurate.

### 4.4 Serving and infrastructure

- Remove the `landing` service from `docker-compose.yml`. Dashboard stays on host port `4000`.
- Port `3000` is free afterward. No redirect is kept (decision 1).
- `apps/landing/Dockerfile` and `nginx.conf` are deleted. The dashboard nginx config already provides SPA fallback and the `/api/` proxy. The landing config added long-lived caching for hashed assets; add the same `location ~* \.(js|css|png|…)$` block with `Cache-Control: public, max-age=…, immutable` to the dashboard nginx.conf, and `no-cache` on `index.html`, so the merge doesn't regress caching.
- Keycloak realm `infrastructure/keycloak/openjcockpit-realm.json`:
  - `rootUrl` / `adminUrl` change from `http://localhost:3000` to `http://localhost:4000`.
  - Remove the `:3000` redirect URIs and web origins. Keep `:4000` and the `:5173` entries.
  - Update the client `description` ("landing page SPA" → "OpenJCockpit web app").
  - Existing Keycloak volumes won't pick up a changed realm import. Note in the migration section that devs must re-import or edit the client manually.
- `scripts/lib/_lib.sh` (URL banner) and `scripts/stack-start.sh` (help text) drop the landing references.
- Vite dev server: the dashboard stays on `4000`; the `'/api'` proxy is unchanged.

### 4.5 CI and tests

- `.github/workflows/frontend-ci.yml`: drop the app matrix. One `npm ci` at the root, then `nx affected -t lint test build format-check` (needs `fetch-depth: 0` and `nx-set-shas` or equivalent so `affected` has a base). Node cache path becomes the root `package-lock.json`. Keep the vitest JUnit output and the failure artifact upload.
- `.github/workflows/e2e-ci.yml`: path filters become `apps/**`, `libs/**`, `e2e/**`, `package-lock.json`; remove the "Install landing dependencies" step and landing cache path.
- `.github/workflows/ci-summary.yml`: frontend path regex becomes `^(apps/|libs/|nx\.json$|package(-lock)?\.json$|…)`.
- e2e (`e2e/`):
  - Remove the separate `landing` Playwright project, its `webServer` entry, `E2E_LANDING_BASE_URL`, and the landing branch in `globalSetup`'s container guard. Drop port 3000 from `e2e/README.md`.
  - Merge `landing-auth.spec.ts` and `landing-portal.spec.ts` into the dashboard project (rename e.g. `hub-auth.spec.ts`, `hub-portal.spec.ts`). Orbs now stay on the same origin, so assertions change from "navigates to `:4000`" to "hub unmounts and project selection / spec-files view mounts".
  - `auth-storage-state.setup.ts` and `auth-login.spec.ts` currently reference landing. Update them for the new post-login destination (the hub), and ensure other dashboard specs that expect to land on project selection first click through the Implementation orb or use a helper. Add one shared helper `enterDashboard(page)` rather than editing every spec.
  - Keep test IDs: `landing-hero`, `intro-greeting`, `portal-orb-business`, `portal-orb-implementation`. `e2e/support/testIds.ts` comments get updated paths.
- Vitest (new, dashboard): hub renders the user's first name, each orb triggers its callback, Logout calls `onLogout`, intro greeting dismisses and doesn't replay (FR-7). Existing `App.*.test.tsx` suites that assert on initial view must be updated for the new `'landing'` entry view (mock or skip through it via a test util in `src/testUtils`).
- `npm run check:styles` and the `visual:*` scripts need to be run once. Add the hub to the visual baseline if the tool supports it (check `visual/`).

### 4.6 Docs

- `README.md`: remove "The landing page only shown with a valid auth token" wording in favor of the integrated hub, fix the link to `apps/landing/src/ui/README.md`.
- `e2e/README.md`: ports, landing project, F3 note.
- `SECURITY.md` has a "landing" mention; update as appropriate.

### 4.7 Convert landing to dashboard conventions (phase 0, in place)

Before anything moves, `apps/landing` is brought up to the dashboard's standard while it is still its own app. Reason: the conversion is then reviewable and verifiable on its own (same visuals before and after), and the later move into `libs/` is a pure file move.

Scope, mirroring `apps/dashboard`:

- **TypeScript.** Rename `.jsx`/`.js` to `.tsx`/`.ts`, add `tsconfig.json` (same strict settings as the dashboard: `strict`, `isolatedModules`, `jsx: react-jsx`, `allowJs: false`), `vite-env.d.ts`, and `tsc` in the `build` script (`tsc && vite build`). Props get explicit types; `PortalOrb`, `Reveal`, `FrameOctagon` etc. are typed, not `any`.
- **CSS Modules.** Each component's `.scss` becomes `<Component>.module.scss`, imported as `styles`, with `localsConvention: 'camelCaseOnly'` in `vite.config` (same as the dashboard). Global selectors in `styles.scss` (`.app-shell`, `.ambient*`, `.scanlines`, `.nav-button`, `.hero`, `.eyebrow`, ...) move into the module of the component that owns them. Use the existing `cx` helper (copy it; it moves to `libs/ui` later).
- **Component layout.** One folder per component with `Component.tsx` and `Component.module.scss` (already the landing layout); screens folder renamed to match dashboard naming where it makes sense. `src/ui/` primitives keep their folder structure (the dashboard has no equivalents for motion, frames, backgrounds, decipher text; they are additive).
- **Design tokens.** `styles/_tokens.scss` and `_reset.scss` modeled on the dashboard's. Landing's `:root` defines `--bg`, `--line`, `--muted`, `--cyan`, `--cyan-soft`, `--text` with values that **differ** from the dashboard's tokens of the same names. Merging both would silently restyle one app. Resolution: all shared names take the dashboard values; landing-only values (`--panel`, `--purple*`, `--green`, `--frame-*`) are either mapped onto an existing dashboard token (e.g. `--violet`, `--surface`) or added as new tokens in the dashboard's `_tokens.scss` under a "hub" group. Any visible color change from this mapping is reviewed in the visual diff and accepted explicitly or compensated per component.
- **Body-level styles.** Landing sets `body { overflow: hidden; background: ... }` and loads Google Fonts via `@import url(...)`. The hub's background and overflow handling move to the hub's own root element; the font import is dropped in favor of whatever the dashboard does for Inter (check `styles/index.scss`, and avoid a runtime request to a third-party host if the dashboard does not already make one).
- **Tooling parity.** Add the dashboard's ESLint config (typescript-eslint, jsx-a11y, react-hooks, eslint-comments with required justifications), Prettier config, `lint`, `lint:fix`, `format`, `format:check` and `test` scripts, Vitest + Testing Library + jsdom setup. Pin dev dependencies to the same exact versions as the dashboard (the landing currently uses caret ranges and `vite@^6.0.7`; the dashboard pins `vite@6.4.3`). Move `@vitejs/plugin-react` and `vite` to `devDependencies`. The existing code is formatted and linted with the changed files only; unrelated formatting is not touched.
- **Visual safety net.** Reuse the dashboard's approach (`visual/run.mjs`, fixtures, a stubbed Keycloak): take a baseline of the hub (with and without the intro overlay, at desktop and a narrow viewport) before the conversion and require a pixel-identical result after, apart from tolerances explicitly accepted for token changes. `check:styles` (unused/missing class names) is added for the landing too.
- **Tests.** Component tests for `LandingPage`, `PortalOrb` (click behavior), `TopBar` (popover, logout), `IntroGreeting` (dismiss), `AuthStatusScreen` (each status), and `Reveal`/`usePrefersReducedMotion`. `getUserFromToken` gets a unit test next to `roles.test.ts`-style tests.
- **Behavior is frozen.** No visible change, no auth change, no renamed test IDs (`landing-hero`, `intro-greeting`, `portal-orb-*`, `auth-status-screen`). Comments about `App()`'s auth effect being a no-touch zone (BR-4/AC-9) refer to a previous change; the conversion may type the effect but must not alter its logic. The StrictMode note in `main.tsx` is kept and updated to `.tsx` paths.
- **Out of scope for phase 0:** merging into the dashboard, Nx, library extraction, redesign, replacing `PuffsBackground`/`DecipherText` implementations.

Acceptance for phase 0: `tsc`, `eslint`, `prettier --check`, `vitest`, `vite build` and the visual check pass in `apps/landing`; e2e landing specs pass unchanged; the landing CI job runs lint/test/build like the dashboard's.

## 5. Migration plan (suggested commits / PR split)

Phase 0: convert `apps/landing` to dashboard conventions in place (§4.7). One PR, or split as: (a) TypeScript + tooling parity, (b) CSS Modules + tokens, (c) tests + visual baseline. It does not depend on Nx and ships first.

Phase 1 (this spec):

1. **Nx workspace, no moves**: root `package.json`, `nx.json`, `tsconfig.base.json`, single lockfile, `apps/dashboard` and `e2e` become workspace projects with their existing scripts as targets. Dockerfile and CI switch to the root context and `nx` commands. Behavior unchanged. `apps/landing` is excluded from the workspace and keeps its own lockfile and CI job until step 5 (decision 7).
2. **Extract `libs/auth` and `libs/ui`** from the dashboard (mechanical move, path aliases, boundary lint rules on). Env-configurable Keycloak (`VITE_KEYCLOAK_*`, current values as defaults); `getUserFromToken` moved in. No behavior change.
3. **Create `libs/landing-hub`**: move the already-converted hub and the primitives it needs from `apps/landing` into `libs/landing-hub` / `libs/ui` (a file move plus import fixes, since phase 0 already did TSX, modules and tests), lazy-loaded, not yet wired.
4. **Wire the hub**: `AppView = 'landing'`, orb callbacks, back-to-hub, logout, session-once intro. Update dashboard Vitest suites.
5. **Infra switch and deletion**: compose (drop `landing` service), nginx caching, Keycloak realm, scripts, CI, e2e restructure, delete `apps/landing`, fix docs/NOTICE.

Each step must leave `nx run-many -t lint test build format-check` and the e2e suite green (step 5 includes the e2e changes). Steps 1–4 are independently mergeable; step 5 must ship in one PR.

Phase 2 (separate spec): extract the existing dashboard features into libraries (§4.1), one PR per library, each with boundary rules enforced.

Phase 3 (only when needed): promote a library into its own app. Requirements at that point: same-origin path-prefixed serving through nginx, shared `libs/auth`, Keycloak client updates.

## 6. Risks

| Risk                                                                                                                           | Mitigation                                                                                                       |
| ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| Same-named design tokens with different values (`--bg`, `--line`, `--muted`, `--cyan`, `--text`) restyle one app when merged   | Token unification in phase 0 (§4.7) with a visual diff                                                           |
| Landing's global SCSS bleeds into or is overridden by dashboard styles (the hub uses `body`, `.app-shell`, `.nav-button` etc.) | Scope everything in modules; compare against the old build with screenshots; `check:styles`                      |
| Canvas background / animation cost on the dashboard bundle                                                                     | `React.lazy` (NFR-3); check bundle size diff in `vite build` output                                              |
| Existing dashboard tests and e2e specs assume the first screen is project selection                                            | Single helper for entering the dashboard; update tests in step 3/4, not ad hoc                                   |
| Existing local Keycloak volumes keep the old client config, causing "invalid redirect_uri" after the move                      | Document the re-import step; `:4000` is already whitelisted so the dashboard keeps working even before re-import |
| Keycloak init double-call under StrictMode                                                                                     | Unchanged; StrictMode stays off                                                                                  |
| Nx adoption churn (root context for Docker, single lockfile, CI rewrite) lands at the same time as the merge                   | Step 1 is behavior-neutral and merged first; the hub work starts only once CI and Docker are green on Nx         |
| `nx affected` misses a change and skips needed checks                                                                          | Run full `run-many` on `main`/`develop` pushes; `affected` only on PRs                                           |
| Over-fragmenting into tiny libraries                                                                                           | Only the four libraries above in phase 1; new libraries need a named feature owner or a second consumer          |
| Library boundaries are advisory unless enforced                                                                                | Boundary lint rule is a CI failure, not a warning                                                                |
| `ci-summary`/branch protection reference matrix job names like `build (landing)`                                               | Landing jobs are not required checks (decision 5)                                                                |

## 7. Acceptance criteria

- **AC-1** `apps/landing` no longer exists; no references to it remain (`grep -ri landing` hits are only the hub's own component/test IDs and this spec's history).
- **AC-2** `docker compose up` starts no `landing` service; the app is reachable at `http://localhost:4000`, nothing at `:3000`.
- **AC-3** Fresh login shows the hub; Implementation and Business orbs reach project selection / spec-files without a page reload or a second Keycloak redirect (verify in the network tab: a single `/auth` redirect).
- **AC-4** `http://localhost:4000/?view=spec-files` still deep-links straight to spec files after project selection.
- **AC-5** Logout works from the hub and from dashboard views.
- **AC-6** In the dashboard, `tsc`, `eslint`, `prettier --check`, `vitest`, `vite build` pass; CI and e2e workflows are green with a single frontend app.
- **AC-7** Opening the dashboard via the deep link doesn't download the hub chunk.
- **AC-8** No hub styles affect other views (visual check passes).

## 8. Decisions

| #   | Question                                                  | Decision                                                                              |
| --- | --------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| 1   | Keep port 3000?                                           | Drop it. No redirect; the `landing` service and `:3000` Keycloak entries are removed. |
| 2   | Intro greeting frequency                                  | Once per browser session (`sessionStorage` flag).                                     |
| 3   | Hub reachable after leaving it?                           | Yes, from the header (FR-5).                                                          |
| 4   | Business orb target                                       | Keep `spec-files`.                                                                    |
| 5   | Required status checks on landing jobs                    | None are required; no branch-protection change needed.                                |
| 6   | Nx remote cache                                           | Local cache only.                                                                     |
| 7   | `apps/landing` in the Nx workspace during steps 1-4       | Excluded; it keeps its own lockfile until deleted in step 5.                          |
| 9   | Convert landing to dashboard conventions before the merge | Yes, as phase 0 in place (§4.7).                                                      |
| 8   | Library import alias                                      | `@openjcockpit/*`.                                                                    |

No open questions remain.

## 9. PR checklist

Every PR: `lint`, `test`, `build`, `format:check` green for the touched projects, e2e green, only changed files formatted.

### Phase 0: convert landing in place

- [x] **PR 1: TypeScript and tooling parity** (`apps/landing`)
  - [x] `.jsx`/`.js` renamed to `.tsx`/`.ts`; props typed, no `any`
  - [x] `tsconfig.json` (strict, same as dashboard), `vite-env.d.ts`, `build` = `tsc && vite build`
  - [x] ESLint (typescript-eslint, jsx-a11y, react-hooks, eslint-comments) and Prettier configs
  - [x] Dev dependencies pinned to the dashboard's versions; `vite` and plugin moved to `devDependencies`
  - [x] Vitest + Testing Library + jsdom set up; `lint`, `format:check`, `test` scripts
  - [x] Auth effect logic untouched (typed only); `main.tsx` StrictMode note updated
- [x] **PR 2: CSS Modules and tokens** (`apps/landing`)
  - [x] Visual baseline of the hub taken **before** any change (with and without intro, desktop and narrow)
  - [x] `*.module.scss` per component, `camelCaseOnly` locals, `cx` helper copied
  - [x] Global selectors from `styles.scss` moved into owning components
  - [x] `_tokens.scss` / `_reset.scss`; same-named tokens take dashboard values; landing-only tokens mapped or added
  - [x] `body` background/overflow moved to the hub root; Google Fonts `@import` handled as per §4.7
  - [x] Visual check passes; accepted color differences listed in the PR description
- [x] **PR 3: tests and checks** (`apps/landing`)
  - [x] Tests: `LandingPage`, `PortalOrb`, `TopBar`, `IntroGreeting`, `AuthStatusScreen`, `Reveal`, `usePrefersReducedMotion`, `getUserFromToken`
  - [x] `check:styles` added
  - [x] `frontend-ci` runs lint/test/build for landing
  - [x] Test IDs unchanged; e2e landing specs pass unchanged

### Phase 1: Nx and merge

- [x] **PR 4: Nx workspace, no moves**
  - [x] Root `package.json` (workspaces), `nx.json`, `tsconfig.base.json`, single `package-lock.json`, Node 22 pin
  - [x] `apps/dashboard` and `e2e` as workspace projects; existing scripts exposed as targets (incl. `check-styles`, `visual`)
  - [x] `apps/landing` excluded, keeps own lockfile and CI job
  - [x] Dashboard Dockerfile builds from repo root; compose context updated
  - [x] `frontend-ci`, `e2e-ci`, `ci-summary` switched to `nx` and root lockfile; `nx affected` on PRs, full run on pushes
  - [x] Local cache only
- [x] **PR 5: extract `libs/auth` and `libs/ui`**
  - [x] `@openjcockpit/*` aliases in `tsconfig.base.json`
  - [x] `src/auth` and `src/ui` moved, imports fixed, no behavior change
  - [x] `VITE_KEYCLOAK_*` env config with current values as defaults; typings added; `getUserFromToken` moved in
  - [x] Module-boundary lint rule and tags enforced in CI; library READMEs
- [x] **PR 6: create `libs/landing-hub`**
  - [x] Hub, intro, orb, top bar moved from `apps/landing`; shared primitives into `libs/ui` (reuse existing Backdrop/Panel/BrandLogo, no duplicates)
  - [x] Props only: `user`, `onSelectPortal`, `onLogout`; no Keycloak import
  - [x] Vendored react-bits file and `ATTRIBUTION.md` moved; `NOTICE` paths updated
  - [x] Exported lazily; not yet wired
- [x] **PR 7: wire the hub into the dashboard**
  - [x] `AppView = 'landing'` as post-login default; `?view=spec-files` deep link bypasses it
  - [x] Implementation orb to project selection/dashboard; Business orb to `spec-files` via existing deep-link path
  - [x] Header brand/Hub entry returns to the hub
  - [x] Logout from hub and dashboard; one Keycloak init, one refresh timer
  - [x] Intro once per session (`sessionStorage`, wrapped in try/catch)
  - [x] Hub chunk not downloaded on the deep-link path
  - [x] `App.*.test.tsx` suites and `src/testUtils` updated for the new entry view; hub tests added
- [x] **PR 8: infra switch and deletion** (single PR)
  - [x] `landing` service removed from `docker-compose.yml`; nothing on `:3000`
  - [x] Dashboard nginx: immutable asset caching, `no-cache` on `index.html`
  - [x] Keycloak realm: `rootUrl`/`adminUrl` to `:4000`, `:3000` URIs/origins removed, client description updated
  - [x] `scripts/lib/_lib.sh`, `scripts/stack-start.sh` landing references removed
  - [x] CI: landing job and path filters removed
  - [x] e2e: landing project, webServer, `E2E_LANDING_BASE_URL`, globalSetup branch removed; hub specs merged into dashboard project; shared `enterDashboard` helper; `testIds.ts` paths updated
  - [x] `apps/landing` deleted; README, `e2e/README.md`, `SECURITY.md`, `NOTICE` updated
  - [x] Note for developers: re-import the Keycloak realm or edit the client manually

### Phase 2 (separate spec)

- [ ] One PR per library: `libs/spec-queue`, `libs/spec-files`, `libs/workflow`, `libs/skills-hub`, `libs/project`, `libs/api`
