# Authentication and local identities

OpenJCockpit uses [Keycloak](https://www.keycloak.org/) for user authentication. The dashboard
uses `keycloak-js`, and the Java services validate Keycloak-issued JWTs as OAuth2 resource servers.
Service-to-service calls use dedicated Keycloak service clients that are seeded locally.

> **Important: local development credentials only.** The passwords are intentionally plaintext in
> `infrastructure/keycloak/openjcockpit-realm.json`, because this is a local dev/import setup. Do
> not use this as-is in production. Every username, password, client secret and key on this page is
> a publicly known, non-production example.

## Keycloak data

Admin for the `master` realm:

- Username: `admin`
- Password: `admin`

Imported realm (from `infrastructure/keycloak/openjcockpit-realm.json`):

- Realm: `openjcockpit`
- Client: `openjcockpit`
- Login theme: `openjcockpit`
- Default locale: `nl`

Standard users in the `openjcockpit` realm:

| Username | Password    |
| -------- | ----------- |
| `tony`   | `Welkom01!` |
| `koen`   | `Welkom01!` |
| `ricky`  | `Welkom01!` |
| `e2e` (`e2e@openjcockpit.local`) | `E2eRunner01!` |

The `e2e` user is a dedicated, local-only seed identity used exclusively by the Playwright E2E suite
(see [Testing](../development/testing.md)). It is deliberately not shared with `tony`/`koen`/`ricky`, so a
flaky or misconfigured test run cannot lock out a real developer's account under the realm's
brute-force protection.

Want your own personal login instead of using the shared users above? See
[infrastructure/keycloak/local-users/README.md](../../../infrastructure/keycloak/local-users/README.md)
for gitignored, per-developer local users.

## Roles

### The `openjcockpit-admin` role

Seeded for `tony` by the realm import and the `keycloak-realm-roles` one-shot (see
[`infrastructure/keycloak/local-users/README.md`](../../../infrastructure/keycloak/local-users/README.md)). It surfaces as `ROLE_openjcockpit-admin` from the
token's `realm_access.roles`. Today it gates changing the spec queue's `autoMergeAllowed` project
setting (see [Spec queue](../workflows/spec-queue.md)).

### No other fine-grained roles

Apart from `openjcockpit-admin`, there are no fine-grained user roles defined in the Keycloak realm.
For example, approving, denying or commenting on a workflow approval gate is available to any
authenticated user. See [Approval gates](../governance/approval-gates.md#no-new-authentication-or-authorization-role-is-introduced).

## Service identities

The local `local-seed` one-shot (`infrastructure/local-seed/seed-local.sh`) creates confidential Keycloak
clients used between services, all with local-only secrets from `.env.example` / `docker-compose.yml`:

| Client | Used by | Purpose |
| --- | --- | --- |
| `openjcockpit-a2a` | LiteLLM A2A callers, `embabel-agent-service` | Client-credentials identity for A2A calls; tokens carry `tenant_id=local`. |
| `openjcockpit-ai-control` | `ai-control-service` | Starts workflows in `embabel-agent-service` and issues git-workspace tickets. |
| `openjcockpit-git-mcp` | `git-mcp-server` | Pulls a running job's Git credentials from `ai-control-service`, server-side. |

The spec queue runner is not a Keycloak identity; it uses a short-lived HS256 token. See
[Spec queue](../workflows/spec-queue.md#the-openjcockpit-admin-role).

The LiteLLM admin UI on port `4100` is a **second authentication surface** that does not use
Keycloak. See [LLM providers](../integrations/llm-providers.md#litellm-gateway).

## Custom login theme

The custom theme has the following location:

```text
infrastructure/themes/openjcockpit/login/
├── login.ftl
├── theme.properties
├── messages/
│   ├── messages_en.properties
│   └── messages_nl.properties
└── resources/
    ├── css/openjcockpit-login.css
    └── js/openjcockpit-login.js
```

The login page remains functionally the standard Keycloak flow: username/password, remember me, reset-password link and any social identity providers are rendered via Keycloak variables. The styling has been replaced with a futuristic OpenJCockpit look, with glow, scanlines, orb, octagon/corner-frame and neon input states.

The realm `openjcockpit` uses `loginTheme: openjcockpit` by default. `docker-compose.yml` mounts the theme into Keycloak:

```yaml
- ./infrastructure/themes:/opt/keycloak/themes:ro
```

For local theme development, the Keycloak theme caches are disabled in Docker Compose:

```yaml
KC_SPI_THEME_CACHE_THEMES: "false"
KC_SPI_THEME_CACHE_TEMPLATES: "false"
KC_SPI_THEME_STATIC_MAX_AGE: "-1"
```

## Re-importing the realm

Keycloak imports the realm on first start into the PostgreSQL database. If the database volume already exists, the existing realm is used. Want to start completely fresh and re-import the JSON?

```bash
docker compose down -v
docker compose up --build
```

> **Warning:** `docker compose down -v` deletes **every** named volume, not only Keycloak's. That
> includes the application databases, LiteLLM keys and usage history, the `workflow-definitions`
> volume (workflow definitions and the policy and approval audit trails) and the Git workspaces.
> Treat it as a destructive action. See [Runbooks](../operations/runbooks.md#docker-compose-down--v-remains-destructive).

**Note for existing developers (landing app removed).** The `openjcockpit` client now uses
`http://localhost:4000` as `rootUrl`/`adminUrl`, and the `:3000` redirect URIs and web origins are gone.
An existing database keeps the old realm, so either re-import it as above or edit the client manually
in the Keycloak admin console (Clients, `openjcockpit`): set Root URL and Admin URL to
`http://localhost:4000` and remove the `:3000` entries. Nothing listens on `:3000` any more.

## Related

- [Security considerations](../governance/security-considerations.md)
- [Quickstart](quickstart.md)
- [Local development](../development/local-development.md): the Vite dev server (`http://localhost:4000`) uses the same registered redirect URIs as the containerised dashboard.
