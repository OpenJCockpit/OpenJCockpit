# @openjcockpit/auth

Keycloak access for the frontend.

- **Exports:** `getKeycloak()` (lazy singleton), `getUserFromToken()` / `User`, `hasRealmRole()`, `isOpenJCockpitAdmin()`, `ADMIN_ROLE`.
- **Configuration:** `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`; defaults are `http://localhost:8080`, `openjcockpit`, `openjcockpit`.
- **Tag:** `type:data`. May depend only on `type:util` libraries (enforced by `@nx/enforce-module-boundaries`).
- **Ownership:** the app's auth effect is the only caller of `init()` and the token refresh; feature libraries receive `user` and callbacks as props and do not import this library.
- **Tests / checks:** `nx run auth:test`, `auth:lint`, `auth:typecheck`, `auth:format-check`.
