# Adding a federated app

The dashboard (`frontend/apps/dashboard`) is the host. It reads `public/federation.manifest.json` at startup,
registers each entry with Module Federation at runtime and loads `<name>/app`. A remote that is down, slow
(5 s) or exports something that is not an `AppDefinition` is skipped and listed in the "Some apps are
unavailable" notice. No host code changes to add an app.

## Steps

1. Create `frontend/libs/<name>-hub` (tag `type:feature`) exporting an `AppDefinition` and its relative routes. See `libs/hello-hub`.
2. Copy `frontend/apps/remote-hello` to `apps/remote-<name>` (tag `type:remote`): set `name` and `exposes['./app']` in `vite.config.ts`, a fixed dev port (4401+), and re-export the definition plus `contractVersion` in `src/app-entry.ts`.
3. Add the lib alias to `tsconfig.base.json` and the remote to the `start:hub` / `build:hub` scripts.
4. Add `"<name>": "<origin>/mf-manifest.json"` to the host manifest.

### A remote with several apps

A remote may export `apps` (an `AppDefinition[]`) instead of `app`; the host accepts either and every entry must pass `isAppDefinition`. `remote-specs` does this: `src/app-entry.ts` re-exports `specsApps as apps`, and `src/specsApps.ts` defines workflow, spec-files and spec-queue, each with its own `route`, `order` and `loadRoutes`. Use it when several apps share one bundle; otherwise use one remote per app.

## Shell contexts

The host provides these; a remote reads them through hooks from `@openjcockpit/hub-contract` and never takes them as props (the host only mounts the remote's routes):

| Hook                                       | Gives                                                                                                                                   |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| `useHubSession()`                          | User, permissions (realm roles).                                                                                                        |
| `useHubProject()` / `useSelectedProject()` | Projects, the selected one, `select(id)`. Wrap pages that need one in `<RequireProject>`, which sends the user to `/projects` and back. |
| `useHubHeader()`                           | The host's header element to render in the remote's layout; undefined in a standalone harness. Link back with `DASHBOARD_PATH`.         |
| `useHubTheme()`                            | `preference`, `resolved`, `setPreference`; the host sets `data-theme` on `<html>`. Style with tokens, not hard-coded colours.           |
| `useAppRegistry()` / `useBadgeFor(id)`     | Host side: the loaded apps and their badge counts.                                                                                      |

HTTP goes through the shared `@openjcockpit/api` singleton (host token, 401 refresh, error translation); the contract has no separate client. `api`, i18n, react-query and `@openjcockpit/ui` are shared singletons, so a remote registers its strings on the host's i18n instance.

## Rules

- `shared` comes from `frontend/module-federation.shared.ts` (react, react-dom, react-router, react-query, i18next, react-i18next, `@openjcockpit/api`, `@openjcockpit/i18n`, `@openjcockpit/ui`, `@openjcockpit/hub-contract`). A private copy of any of them breaks hooks or the session context.
- Remotes read the session with `useHubSession()` and never import `keycloak-js` or `@openjcockpit/auth` (lint rule).
- Gate pages with `<RequirePermission any={[...]}>`; permissions are Keycloak realm roles.
- The contract is additive only. Bump `HUB_CONTRACT_VERSION` only when a host must understand the change; the host rejects a remote with a higher version.
- Manifest origins must be the host origin, listed in `/config.json` `federation.allowedOrigins` (or `VITE_FEDERATION_ALLOWED_ORIGINS`), or localhost in dev.

## Settings sections

An app contributes forms to the host's Settings page through `AppDefinition.settings` (contract version 2). The host owns the URL (`/settings/<id>`, or `/project-settings/<id>` for sections with `screen: 'project'`), the sub-navigation, the heading (`titleKey`/`descriptionKey`, resolved in the host's `dashboard` namespace under `settings.sections.*`, like widget titles) and the "select a project" guard (`requiresProject`).

- `component` is lazy and content only: no `AppShell`, header, back button or `<h1>`. Its default export wraps itself in the remote's translation provider.
- `requiredPermissions` is any-of; a section the session may not open is hidden and its URL answers 403. Ids must be unique across apps (the first wins, the rest are ignored with a warning).
- Order sections with `order` (bands: Workspace 10-99, Integrations 100-199, Project integrations 200-299) and group them with `groupKey`.
- `screen` (`'settings'`, the default, or `'project'`) picks the screen: global Settings or the selected project's settings, each with its own sidebar entry and sub-navigation. Put sections that configure a project (or need one) on `'project'`.
- An app that only contributes settings sets `showInNav: false`.
- A remote at contract version 1 keeps working but contributes no settings.

## Deployment

One image per deployable. The host (`apps/dashboard/Dockerfile`) and each remote (`deploy/remote/Dockerfile`, `--build-arg REMOTE=remote-<name>`) are built from `frontend/`.

Host env, rendered by nginx at container start (all `no-store`):

| Variable                          | Default | Effect                                                                                            |
| --------------------------------- | ------- | ------------------------------------------------------------------------------------------------- |
| `FEDERATION_MANIFEST`             | `{}`    | JSON object served as `/federation.manifest.json`: name to `https://<origin>/mf-manifest.json`.   |
| `FEDERATION_ALLOWED_ORIGINS_JSON` | `[]`    | Served as `/config.json` `federation.allowedOrigins`; the host drops manifest entries outside it. |
| `FEDERATION_ALLOWED_ORIGINS`      | empty   | Space-separated origins added to the CSP `script-src`, `style-src` and `connect-src`.             |

Keep the last two in step. Remote env: `HOST_ORIGIN` (default `http://localhost:4000`) is the only origin CORS allows. The remote serves `mf-manifest.json` and `remoteEntry.js` as `no-store`, `/assets/` as immutable, and answers a missing file with a plain 404 (no HTML fallback).

`docker-compose.yml` runs the host plus the three remotes on ports 4401, 4403 and 4404 with these variables set.

Adding an app to a deployed hub: deploy the remote image, then add its origin to the three host variables and restart the host.

Governance gates in CI (`frontend-ci.yml`): `frontend/scripts/check-remotes.mjs` (each remote exposes `./app`, shares the singletons, bundles no identity SDK, and its `app-entry` reaches the contract only through the shared scope, never a private copy); both image jobs smoke-test the endpoints above; the host's contract version gate rejects a remote with a higher `HUB_CONTRACT_VERSION` at runtime.
