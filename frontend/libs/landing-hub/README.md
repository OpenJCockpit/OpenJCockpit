# @openjcockpit/landing-hub

The authenticated landing hub: intro greeting, top bar and the Business / Implementation portal orbs.

- **Tag:** `type:feature`. May depend on `type:ui`, `type:data` and `type:util` libraries (enforced by `@nx/enforce-module-boundaries`).
- **API:** props only. `LandingHub` takes `user` (`HubUser`), `onSelectPortal(portal)` and `onLogout()`; it never imports Keycloak. The host decides what a portal selection or logout means.
- **Lazy:** `LandingHub` is a `React.lazy` component, so the hub (and its canvas background) is a separate chunk that is only fetched when rendered. Render it inside `<Suspense>`.
- **Shared primitives:** motion, frames, the particle background and the vendored react-bits `DecipherText` live in `@openjcockpit/ui`.
- **Not wired yet:** nothing in `apps/dashboard` renders the hub until the follow-up PR.
- **Tests / checks:** `nx run landing-hub:test`, `landing-hub:lint`, `landing-hub:typecheck`, `landing-hub:format-check`.
