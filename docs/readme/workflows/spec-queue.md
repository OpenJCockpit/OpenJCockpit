# Spec queue

The spec queue lets a project process specifications one after another: enqueue specs, and a
background runner in `ai-control-service` starts the next workflow run when the previous item is
done, optionally auto-merging its pull request.

The public documentation site has additional detail on auto-merge risk, poll errors and failure
reasons: [openjcockpit.github.io/OpenJCockpit](https://openjcockpit.github.io/OpenJCockpit/)
(source: [`docs-site/index.html`](../../../docs-site/index.html)).

## How to use it

- There is one queue per project. Items run one at a time; a manual workflow start is blocked with
  `409 SPEC_QUEUE_ITEM_ACTIVE` while an item is active.
- Auto-merge is off per item by default. The project setting `autoMergeAllowed` defaults to `false`
  and only users with the `openjcockpit-admin` realm role can change it
  (`PUT /api/projects/{projectId}/spec-queue/settings`).
- Actions on items: enqueue, edit, reorder, cancel or remove, retry or skip a failed item, plus
  pause/resume of the queue. A failed item halts the queue; retry or skip it (or remove it), then
  resume the queue.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SPEC_QUEUE_RUNNER_TOKEN_SECRET` | local-only placeholder | HS256 key shared by `ai-control-service` and `embabel-agent-service`. Set your own (>= 32 UTF-8 bytes) outside local use. Blank = runner unconfigured; non-blank under 32 bytes fails startup. |
| `SPEC_QUEUE_RUNNER_ENABLED` | `true` | `false` stops the runner from starting queued items. |
| `SPEC_QUEUE_POLL_INTERVAL` | `PT20S` | Duration between runner ticks (at least `PT1S`). |

`SPEC_QUEUE_START_LEASE`, `SPEC_QUEUE_MERGE_LEASE` (`PT5M`), `SPEC_QUEUE_MERGE_WAIT_TIMEOUT` (`PT30M`),
`SPEC_QUEUE_EMBABEL_TIMEOUT` and `SPEC_QUEUE_GITHUB_TIMEOUT` (`PT30S`) are read by the backend
`application.yml` only. The runner never retries a call that may have had an effect: a merge request is
sent at most once, and a merge or workflow start whose outcome is unclear is decided by a read (or fails
the item), never by sending it again.

`SPEC_QUEUE_MERGE_WAIT_TIMEOUT` bounds how long an auto-merge item waits for its pull request to become
mergeable (checks pending, mergeability still unknown). When it elapses the item fails with `MERGE_WAIT_TIMEOUT`
and the queue halts, so one stuck pull request cannot hold the project's active slot forever.

## The `openjcockpit-admin` role

Seeded for `tony` by the realm import and the `keycloak-realm-roles` one-shot (see
[`infrastructure/keycloak/local-users/README.md`](../../../infrastructure/keycloak/local-users/README.md)). It surfaces as `ROLE_openjcockpit-admin` from the
token's `realm_access.roles`. The runner itself is not a Keycloak identity: it uses a 60 s HS256 JWT
signed with the shared secret.

## GitHub token scopes

Pull requests read/write, Contents write, Checks read, Commit statuses read (classic: `repo`), and a
role that is allowed to merge. Merging is GitHub-only.

## Residual risks

- One item is active per project, so a long run or an unmerged pull request holds the queue.
- A workflow start whose outcome is unknown fails the item with `START_OUTCOME_UNKNOWN` and halts the
  queue. The run may still exist; the runner has no privilege to stop it, so a human has to check the
  workflow executions.
- Only items with exactly one pull request can be auto-merged; with several, the queue waits for a
  human merge.
- The runner discovers an item's pull request from the run's `pull-request:` artifact. The default
  workflows currently push a branch without emitting such an artifact, so auto-merge does not apply to
  them. See [Git integration](git-integration.md#pull-requests-and-merging).

## Rollback

Rollout order: contract and migration V14, embabel, ai-control, compose secret and role seed,
dashboard. Switch the feature off with `SPEC_QUEUE_RUNNER_ENABLED=false`. Dropping the four queue
tables is a manual, destructive step; never use `docker compose down -v`.

## Related

- [Agents and workflows](agents-and-workflows.md)
- [Git integration](git-integration.md)
- [Authentication](../getting-started/authentication.md)
- [Testing](../development/testing.md): the spec queue is a named gap in the E2E suite.
