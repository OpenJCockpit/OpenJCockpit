# Frontend architecture

Design of the dashboard frontend (`frontend/`). For day-to-day rules see [frontend-guidelines.md](frontend-guidelines.md). For adding an app see [hub-app-integration-guide.md](hub-app-integration-guide.md).

## Goals

- **Independently deployable apps.** Specs, skills and other areas ship as separate bundles (remotes) and plug into one shell (the host) at runtime, with no host code change.
- **One session, one API client, one theme.** Identity, HTTP, i18n and styling are owned by the host and shared, never duplicated per app.
- **Enforced structure.** Layering, shared singletons and the remote contract are checked by lint and CI, not by convention.

## Workspace layout

Nx workspace in `frontend/`, npm workspaces `apps/*`, `libs/*`, `e2e`.

| Project | Tag | Role |
| --- | --- | --- |
| `apps/dashboard` | `type:app` | Host: auth, shell, routing, federation loader, settings and landing. |
| `apps/remote-hello`, `remote-skills-hub`, `remote-specs` | `type:remote` | Federated bundles. Only re-export a feature lib's `AppDefinition`. |
| `libs/*-hub`, `workflow`, `project`, `spec-files`, `spec-queue`, `skills-hub`, `landing-hub` | `type:feature` | Screens and feature logic. |
| `libs/hub-contract` | `type:contract` | Host/remote contract: `AppDefinition`, session, project, header, theme, permission hooks. |
| `libs/ui` | `type:ui` | Components and design tokens (CSS modules + SCSS). |
| `libs/api`, `libs/auth` | `type:data` | HTTP clients and DTO types; Keycloak session. |
| `libs/i18n` | `type:util` | Locales, formatting helpers. |

## Layering

Enforced by `@nx/enforce-module-boundaries` in `eslint.config.base.js`; a violation fails CI.

```
app / remote  →  feature  →  ui, data, util, contract
contract      →  util, data
ui, data      →  util
util          →  util
```

Consequences worth remembering:

- `libs/api` must not import `@openjcockpit/ui` (it is `type:data`).
- Features never import each other directly. Cross-feature needs go through the contract or the host.
- A remote contains no feature code, only its entry and Vite/Module Federation config.

## Host and remotes

The host reads `public/federation.manifest.json`, registers each entry with Module Federation at runtime and loads `<name>/app`. A remote that is down, slow (5 s) or exports something that fails `isAppDefinition` is skipped and listed in the "Some apps are unavailable" notice.

```
browser ─► dashboard (host)
            ├─ auth (Keycloak) ─► setAuthToken / session context
            ├─ federation loader ─► remote-specs, remote-skills-hub, remote-hello
            └─ providers: query client, i18n, router, theme, project, app registry
```

### Contract

`AppDefinition` (in `libs/hub-contract`) declares an app's route, order, nav visibility, lazy routes, widgets and settings sections. Remotes read host state through hooks (`useHubSession`, `useHubProject`, `useHubHeader`, `useHubTheme`, `useAppRegistry`) and are never handed it as props.

- **Additive only.** `HUB_CONTRACT_VERSION` is bumped only when a host must understand the change. The host rejects a remote with a higher version; an older remote keeps working with fewer features (version 1 remotes contribute no settings).
- **Permissions are Keycloak realm roles**, checked with `RequirePermission` / `Can` / `useCan`. `ADMIN_PERMISSION` and `FORBIDDEN_PATH` come from the contract.

### Shared singletons

`frontend/module-federation.shared.ts` lists what is shared: react, react-dom, react-router, react-query, i18next, react-i18next, `@openjcockpit/api`, `i18n`, `ui`, `hub-contract`. A private copy of any of them breaks something concrete:

| Library | Failure with a private copy |
| --- | --- |
| react / react-dom | Hooks throw. |
| react-router | Router context missing. |
| react-query | Separate cache, no invalidation across apps. |
| i18n | Remote strings not registered on the host instance. |
| api | Auth token lives in module state, so the remote is unauthenticated. |
| hub-contract | A different `HubSessionContext`, so `useHubSession()` returns nothing. |

`scripts/check-remotes.mjs` verifies this against the build output in CI: each remote exposes `./app`, shares the singletons, bundles no identity SDK, and reaches the contract only through the share scope.

## Data flow

1. **Auth.** `useAuthSession` signs in through `libs/auth` and writes the token with `setAuthToken` (the only writer). A 401 calls the registered session refresher once and retries.
2. **HTTP.** Every call goes through `apiRequest` / `apiRequestVoid` in `libs/api/src/core/http.ts`. ESLint bans raw `fetch` elsewhere in that lib. Requests take an `AbortSignal`.
3. **Types.** DTO types are generated from `shared/contracts/openapi.yaml` with Orval (types only; clients are hand-written). Change the contract, run `nx run api:generate`, commit the output. CI fails on stale output.
4. **Server state.** react-query. Default `staleTime` is 30 s. Only network errors retry (once); an `ApiError` is deterministic and fails immediately.
5. **Errors.** Server error codes map to localised messages through the registered error translator. Render errors are caught by `ErrorBoundary` from `libs/ui`, which resets when its `resetKeys` change.

## UI system

- Design tokens are CSS custom properties in `libs/ui/src/styles/_tokens.scss`, the single source of truth. The host sets `data-theme` on `<html>`.
- Components live in `libs/ui`, one folder each with a `*.module.scss` and generated `*.module.scss.d.ts` typings (`npm run typings`).
- Container choice for forms and confirmations follows [ui-containers.md](ui-containers.md).
- Accessibility baseline: `jsx-a11y` lint, axe checks in tests, and `ModalShell` providing focus trap, Escape, focus restore and inert background.

## Internationalisation

All user-facing copy goes through `t()` from `@openjcockpit/i18n`. One namespace per lib, strings in `<lib>/src/locales/<locale>.json`, English as source. `npm run check:i18n` fails when a locale lacks keys that `en.json` has. A remote registers its strings on the host's i18n instance. Details are in `CONTRIBUTING.md`.

## Build, test and deploy

- **Quick gate:** `npm run check` runs lint, typecheck, test, format-check, check-styles and check-i18n across the workspace. Use `npx nx affected -t lint typecheck test build` while iterating.
- **TypeScript:** `strict` and `noUncheckedIndexedAccess` on.
- **Tests:** Vitest and Testing Library per project; Playwright in `frontend/e2e` against a running stack. The e2e suite does not cover agent pipelines (see `e2e/README.md`).
- **No backend needed for UI work:** `npm run start:hub:mock` runs everything against an in-memory API ([frontend-mock-server.md](frontend-mock-server.md)). Mock code is never in production builds.
- **Images:** one per deployable (host plus one per remote). CORS on a remote allows only `HOST_ORIGIN`. Manifest origins must be on the host's allow-list; see the deployment section of the integration guide.

## Design decisions

| Decision | Reason | Cost |
| --- | --- | --- |
| Runtime federation with a manifest, not build-time composition | Add or remove an app by deploying an image and editing env vars | Shared-singleton discipline, CI gate needed |
| Contract exposed as hooks, not props | The host mounts only the remote's routes, so the contract can grow without changing mount code | Remotes need the host context to run; a standalone harness must provide stubs |
| Hand-written API clients over generated ones | Control over auth, abort, retry and error translation | Types and clients can drift; mitigated by generating the types |
| Layer tags enforced in lint | Keeps `ui`/`data` free of feature code and prevents import cycles | New libs must choose a tag |
| Tokens as CSS variables | Theme switching without a rebuild and shared across federated bundles | No compile-time check of token names |

## Known gaps

- `i18next/no-literal-string` is a warning and becomes an error when the translation migration finishes.
- Project settings can show several inline forms at once (see [ui-containers.md](ui-containers.md)).
