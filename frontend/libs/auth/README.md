# @openjcockpit/auth

Keycloak access for the frontend.

- **Exports:** `getKeycloak()` (lazy singleton), `getUserFromToken()` / `User`, `hasRealmRole()`, `isOpenJCockpitAdmin()`, `ADMIN_ROLE`.
- **Configuration:** at runtime `loadKeycloakConfig()` reads `/config.json`, which the dashboard container renders from `KEYCLOAK_URL`, `KEYCLOAK_REALM` and `KEYCLOAK_CLIENT_ID` (the same `KEYCLOAK_URL` drives the CSP). Call it before the first `getKeycloak()`. If the file is missing or malformed, the build-time `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID` apply; defaults are `http://localhost:8080`, `openjcockpit`, `openjcockpit`.
- **Tag:** `type:data`. May depend only on `type:util` libraries (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the app's auth effect is the only caller of `init()` and the token refresh; feature libraries receive `user` and callbacks as props and do not import this library.
- **Tests / checks:** `nx run auth:test`, `auth:lint`, `auth:typecheck`, `auth:format-check`.
