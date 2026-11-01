# Authorization model

OpenJCockpit authorizes every API call by **Keycloak realm role**, and it **denies by default**. A
signed-in user with no OpenJCockpit role can authenticate but can call nothing. Each dashboard API route
is mapped to the least role allowed to call it, and any route without a mapping is refused, including a
newly added endpoint.

## Roles

| Realm role              | Grants                                                                                                                                                       | Implies                     |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| `openjcockpit-admin`    | Everything: project create/update/activate/deactivate, git credentials, project integration config (Hermes, Jira, document folder), OPA config, spec-queue settings, skills-marketplace connections, A2A services, actuator metrics/loggers | operator, approver, viewer  |
| `openjcockpit-operator` | Start and stop workflow runs (including preflight), manage workflows, workflow groups, agent/skill/subagent definitions, spec files, spec init, git check and spec queues | viewer                      |
| `openjcockpit-approver` | Decide (accept/deny) workflow approval gates                                                                                                                 | viewer                      |
| `openjcockpit-viewer`   | Read-only: the listed `GET` endpoints, plus the side-effect-free `POST /api/model-routing/decision`                                                          | none                        |

Operator and approver are **independent**: an operator cannot decide a gate, and an approver cannot start
a run. To grant both, assign both roles.

The implications live in **one place**: the Spring Security role hierarchy in
`services/service-common/.../security/OpenJCockpitRoles.java`, which both services use. Keycloak assigns
only the direct roles. Do **not** make these roles composite in Keycloak, because then the realm and the
code define the hierarchy separately and can drift apart.

Role names are case-sensitive. Tokens carry them in `realm_access.roles`, and they map to the
`ROLE_<name>` authorities. Client roles (`resource_access`) are ignored. Keycloak's built-in realm roles
(`default-roles-openjcockpit`, `offline_access`, `uma_authorization`) grant nothing.

## Where it is enforced

- **ai-control-service** (the dashboard's API) is the primary enforcement point. URL rules in
  `config/SecurityConfig` map every route and end with `anyRequest().denyAll()`. Read routes are listed one
  by one, with no `/api/**` wildcard. `SpecQueueController#updateSpecQueueSettings` also carries an
  `@PreAuthorize` for admin.
- **embabel-agent-service** re-checks the important mutations (defence in depth). ai-control relays the
  user's own bearer token for user-initiated calls, so the user's realm roles reach embabel too. Approval
  decisions need approver. Workflow, workflow-group and definition mutations need operator. Starting a
  run needs operator, the spec-queue runner or ai-control's service identity. Stopping a run needs operator
  or ai-control's service identity. OPA and A2A configuration changes need admin. Reads remain open to any
  authenticated caller except the runner.

An authenticated caller without the required role gets **403** with an `ApiErrorResponse` body
(`{"code":"FORBIDDEN","message":"You do not have permission to perform this action. Ask an administrator for the required role."}`)
and a `WWW-Authenticate: Bearer ...` challenge header (with `error="insufficient_scope"` for token-authenticated callers). The dashboard shows the message. A
missing or invalid token gets **401**. `/actuator/health` and `/actuator/info` are public.

## Endpoint → role matrix (ai-control-service)

`{id}` placeholders are any value. "authenticated" means any valid token, after which the controller
checks the caller's client.

| Method | Path | Role |
|---|---|---|
| GET | `/internal/git-workspaces/{workspaceKey}/credentials` | authenticated; the controller requires `azp` = `openjcockpit.git-workspace.credential-client-id` |
| GET | `/actuator/health`, `/actuator/info` | public |
| any | `/actuator/**` (metrics, loggers) | admin |
| GET | `/api/workspaces/{customerId}`, `/api/specs/{customerId}` | viewer |
| POST | `/api/model-routing/decision` | viewer |
| GET | `/api/projects`, `/api/projects/all`, `/api/projects/{id}/git-status` | viewer |
| POST | `/api/projects` | admin |
| PUT | `/api/projects/{id}` | admin |
| PATCH | `/api/projects/{id}/activate`, `/deactivate`, `/new-project` | admin |
| GET | `/api/projects/{id}/hermes-config`, `/jira-config`, `/document-folder-config` | viewer (secrets are write-only) |
| POST, DELETE | `/api/projects/{id}/hermes-config`, `/jira-config`, `/document-folder-config` | admin |
| GET, POST, DELETE | `/api/projects/{id}/git-credentials` | admin |
| POST | `/api/projects/{id}/git-check` | operator |
| GET | `/api/projects/{id}/spec-files`, `/spec-init/status` | viewer |
| PUT | `/api/projects/{id}/spec-files` | operator |
| POST | `/api/projects/{id}/spec-init` | operator |
| GET | `/api/projects/{id}/spec-queue`, `/spec-queue/settings` | viewer |
| POST | `/api/projects/{id}/spec-queue/items`, `/items/{itemId}/retry`, `/items/{itemId}/skip`, `/pause`, `/resume` | operator |
| PATCH, DELETE | `/api/projects/{id}/spec-queue/items/{itemId}` | operator |
| PUT | `/api/projects/{id}/spec-queue/order` | operator |
| PUT | `/api/projects/{id}/spec-queue/settings` | admin |
| GET | `/api/agent-runs/{runId}`, `/approval-gate`, `/approval-decisions` | viewer |
| DELETE | `/api/agent-runs/{runId}` (stop run) | operator |
| POST | `/api/agent-runs/{runId}/approval-gate/decision` | approver |
| GET | `/api/agent-catalog`, `/api/agent-catalog/providers/{provider}/agents/{name}/card` | viewer |
| GET | `/api/{agent,skill,subagent}-definitions`, `/{name}` | viewer |
| POST | `/api/{agent,skill,subagent}-definitions`, `/generate-from-prompt` | operator |
| PUT, DELETE | `/api/{agent,skill,subagent}-definitions/{name}` | operator |
| POST | `/api/agentic-workflows/start`, `/api/agentic-workflows/preflight` | operator |
| GET | `/api/git-workspace-jobs/{jobId}`, `/events` | viewer |
| GET | `/api/workflows`, `/export`, `/{id}`, `/{id}/decision-logs`, `/{id}/executions`, `/{id}/executions/{runId}` | viewer |
| GET | `/api/workflow-executions/{id}/decision-logs` | viewer |
| POST | `/api/workflows`, `/api/workflows/import`, `/api/workflows/{id}/start` | operator |
| PUT, DELETE | `/api/workflows/{id}` | operator |
| GET | `/api/workflow-groups`, `/{id}` | viewer |
| POST | `/api/workflow-groups` | operator |
| PUT, DELETE | `/api/workflow-groups/{id}` | operator |
| GET | `/api/opa-config`, `/api/opa-config/health` | viewer |
| PUT | `/api/opa-config` | admin |
| GET | `/api/skills-marketplaces`, `/api/skill-catalog`, `/api/a2a-services` | viewer |
| POST | `/api/skills-marketplaces`, `/api/a2a-services` | admin |
| PUT, DELETE | `/api/skills-marketplaces/{id}`, `/api/a2a-services/{id}` | admin |
| anything else | | **denied** (403, or 401 without a token) |

The tests pin this matrix in
`services/ai-control-service/src/test/java/nl/metafactory/aicontrol/config/AuthorizationMatrixTest.java`,
which checks every endpoint against every persona. `SecurityConfigTest#everyHandlerMappingIsInTheAuthorizationMatrix`
fails when a controller route is added without a matrix entry, or when the matrix lists a route that no
longer exists.

**Adding an endpoint:** add a matcher to `SecurityConfig.authorizationRules` with the least role, add the
row to `AuthorizationMatrixTest.ENDPOINTS` and to this table. Until then the endpoint answers 403.

## Service identities

| Identity | Recognised by | Allowed |
|---|---|---|
| Spec-queue runner (ai-control → embabel) | Token from the runner issuer (`openjcockpit.security.spec-queue-runner`), authority `SPEC_QUEUE_RUNNER` | embabel only: `POST /api/workflows/*/start` and `GET /api/workflows/*`, `/api/workflow-groups/*`, `/api/agent-runs/*`. Everything else is 403. |
| ai-control-service (client credentials) | embabel: `azp` = `openjcockpit.git-workspace.trusted-client-id` (default `openjcockpit-ai-control`), authority `AI_CONTROL_SERVICE` | embabel: start (`POST /api/workflows/*/start`) and stop (`DELETE /api/agent-runs/*`) runs, reads, `POST /internal/git-workspace-tickets` |
| Git MCP server | ai-control: `azp` = `openjcockpit.git-workspace.credential-client-id` | ai-control: `GET /internal/git-workspaces/*/credentials` only |
| embabel-agent-runtime | embabel: `azp` = the configured runtime client id | embabel: `/internal/agent-runtime/v1/**` |

The `/internal/**` endpoints check the `azp` claim in their controller. A human token, even an admin's,
is rejected there with 403.

## Project scope

OpenJCockpit has **no project membership**. `Project.owner` is a free-text label and is not tied to any
identity. Project scope is therefore **enforced by role only**. An operator can run workflows and manage
spec queues for every project, a viewer can read every project, and an approver can decide gates on every
run.

This is a **known limitation**. Per-project authorization would need a membership model (users or groups
per project) and a check on every project-scoped route, including routes that address a run or workflow
rather than a project. Until that exists, split tenants across separate deployments or realms, not
separate projects.

## Assigning roles in Keycloak

**Admin console:** realm `openjcockpit` → *Users* → user → *Role mapping* → *Assign role* → filter by
realm roles → select one or more `openjcockpit-*` roles. Users must sign in again (or refresh their
token) before the change applies.

**Groups (recommended for teams):** create groups such as `openjcockpit-operators`, assign the realm role
to the group under *Role mapping*, and add users to the group.

**Default role:** `default-roles-openjcockpit` must not contain any `openjcockpit-*` role, so that new
users get nothing until an administrator grants a role. `seed-realm-roles.sh` removes any such role from
the default role.

**Local development** (`docker compose`):

- `infrastructure/keycloak/openjcockpit-realm.json` defines the four roles and assigns them on a fresh
  import:

  | User | Roles |
  |---|---|
  | `tony` | admin |
  | `koen` | operator, approver |
  | `ricky` | operator |
  | `e2e` | operator, approver (the Playwright suite creates, starts and deletes workflows; it must not be admin) |

- `infrastructure/keycloak/seed-realm-roles.sh` (the `keycloak-realm-roles` one-shot container) applies
  the same result to an existing Keycloak database, where the realm import is skipped. It creates
  missing roles, maps users idempotently and keeps the roles out of the default role. The users per role
  come from `KC_ADMIN_ROLE_USERS` (default `tony`), `KC_OPERATOR_ROLE_USERS` (`koen ricky e2e`),
  `KC_APPROVER_ROLE_USERS` (`koen e2e`) and `KC_VIEWER_ROLE_USERS` (none). It refuses to make `e2e` an
  admin.
- `infrastructure/keycloak/seed-e2e-user.sh` also maps `E2E_SEED_ROLES` (default operator and approver) to
  the e2e user, whichever seeding container runs first.
- Per-developer users from `infrastructure/keycloak/local-users/*.local.json` need explicit `realmRoles`
  (for example `["openjcockpit-operator"]`). With an empty list they can sign in but are refused
  everywhere.

## Dashboard behaviour

The dashboard hides only the spec-queue auto-merge toggle for non-admins (`isOpenJCockpitAdmin()`, a UI
hint only). Other admin and operator actions stay visible. When a user lacks the role, the server answers
403 and the dashboard shows the server's message in its usual error area.
