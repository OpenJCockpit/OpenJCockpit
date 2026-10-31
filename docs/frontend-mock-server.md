# Frontend mock server

Runs the whole frontend without the backend, Keycloak or a database. It is a development tool only: it is never registered for production builds and adds nothing to the bundle.

## Start it

From `frontend/`:

```
npm run start:hub:mock
```

This starts the same four dev servers as `start:hub` (dashboard on 4000, remotes on 4401, 4403 and 4404) with `MOCK_API=1`. To run the dashboard alone:

```
npm run dev:mock -w @openjcockpit/dashboard
```

You are signed in as `dev` with the `openjcockpit-admin` role. Open http://localhost:4000, pick a project on the projects page and work as usual.

## How it works

`MOCK_API=1` makes `apps/dashboard/vite.config.ts` do two things:

1. Register the `mockApi()` Vite plugin. Its middleware answers every `/api/*` request from an in-memory store, ahead of the dev proxy to the backend. The remote apps run inside the dashboard page, so their requests reach the same middleware.
2. Alias `./keycloak` (in `libs/auth`) to `mock/keycloak.stub.ts`, which signs in immediately with a fixed token. No application code changes.

| File                                   | Purpose                                                                                          |
| -------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `apps/dashboard/mock/mockApi.ts`       | The Vite plugin: matches method and path, parses the JSON body, sends the response, maps errors. |
| `apps/dashboard/mock/routes.ts`        | The endpoints, one `get(...)`, `post(...)`, `put(...)`, `patch(...)` or `del(...)` line each.    |
| `apps/dashboard/mock/store.ts`         | The in-memory state, seed data and small helpers (`upsert`, `remove`, `perProject`).             |
| `apps/dashboard/mock/keycloak.stub.ts` | Replaces the Keycloak module.                                                                    |
| `apps/dashboard/visual/fixtures.mjs`   | Seed data, shared with the visual-regression runner.                                             |

## State

State lives in the dev server process. Edits survive page reloads and are lost when the server restarts, which resets everything to the seed data. The seed is copied with `structuredClone`, so the visual-regression fixtures are never modified.

| Area                          | Behaviour                                                                                                                                                           |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Projects                      | Create, edit, activate and deactivate, new-project flag, git credentials, git check. Git credential secrets are only reported as `hasSecret`.                       |
| Integration settings          | Hermes, Jira, document-folder (per project) and OPA. Secrets are reported as `hasAuthToken`.                                                                        |
| Spec files                    | Per project. Saving a file adds or replaces it.                                                                                                                     |
| Spec queue                    | Enqueue, edit, remove, reorder, pause, resume, retry, skip and the auto-merge setting. Nothing runs the items, so they stay `QUEUED` until changed.                 |
| Workflows and groups          | Full create, update and delete, plus import and export.                                                                                                             |
| Definitions                   | Agents, subagents and skills, keyed by name. `generate-from-prompt` returns a stub built from the prompt and does not save it.                                      |
| A2A services and marketplaces | Full create, update and delete.                                                                                                                                     |
| Catalogs                      | Derived from the data above: local agents and skills from the definitions, one provider per A2A service, one sample external skill per enabled marketplace.         |
| Runs                          | Starting a workflow creates a run that reads as `RUNNING` for 5 seconds and `COMPLETED` after that. It appears in the executions list and in `/api/agent-runs/:id`. |

## Not simulated

- **Approval gate.** `GET /api/agent-runs/:id/approval-gate` returns 404 because no run waits for approval. Decision logs are always empty.
- **Spec queue runner.** Queued items never start.
- **OPA.** The health check reports reachable only while OPA is enabled.
- **Validation.** Request bodies are not validated. A bad request is stored as sent.
- **Identity.** Everything is created by `dev`, and roles are not enforced.

## Add or change an endpoint

1. Find the call in `frontend/libs/api/src` and read its response type in `libs/api/src/generated/contract.ts`. A wrong shape usually crashes a view instead of showing an error.
2. Add a line to `routes.ts`:

   ```ts
   get("/api/projects/:id/example", ({ p, query, body }) => example(p[0]));
   ```

   `:name` matches one path segment, and `p` holds the captures in order. Return the response body. Returning nothing sends `{}`. Throw `notFound('Thing')` or `new MockError(status, code, message)` for an error response.

3. Keep state in `store.ts`. Use `perProject` for data that exists once per project.
4. Register fixed paths before parameterised ones, for example `/api/workflows/export` before `/api/workflows/:id`.

A request with no matching route returns a 404 with code `MOCK_NOT_FOUND` and logs `[mock-api] no mock for <METHOD> <path>` in the dev server output.

## Seed data

Change the lists in `apps/dashboard/visual/fixtures.mjs` to change what a fresh server starts with. The visual-regression screenshots use the same file, so re-run `dashboard:visual-baseline` after changing it. The workspace comes from `libs/api/src/testing/mockWorkspace.ts`.
