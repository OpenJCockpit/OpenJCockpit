# Operations runbooks

Operational procedures and signals for the local stack. Feature-specific runbooks live with their
feature:

- [Approval gate runbook](../governance/approval-gates.md#operations-runbook): tunables, behaviour
  changes, rollback hazard, paused runs.
- [Spec queue rollback](../workflows/spec-queue.md#rollback).
- [Per-run Git workspace cleanup and rollback](../integrations/mcp-tools.md#per-run-workspace-isolation-workspacekey).
- [LLM reverts A and B](../integrations/llm-providers.md#reverting-the-default-llm).
- Historical: [workflow agent-id reconciliation runbook](../reference/historical-notes.md#operations-runbook-workflow-agent-id-reconciliation).

## Services, ports and health

| Service | Host port | Health |
| --- | --- | --- |
| `dashboard` | `4000` | HTTP |
| `keycloak` | `8080` | Compose healthcheck |
| `litellm` | `4100` | Compose healthcheck on `/health/liveliness` (never `/health`, which calls every model) |
| `ai-control-service` | `9080` | `/actuator/health` |
| `embabel-agent-service` | `8091` | `/actuator/health` (readiness includes the database) |
| `embabel-agent-runtime` | `8092` | Compose healthcheck on `/actuator/health/readiness`; `/actuator/health/agentDefinitions` for the served agent definitions |
| `opa` | `8181` | `/health` |
| `git-mcp-server` | not published | `/actuator/health` inside the network |
| `postgres` | not published | Compose healthcheck |

`/actuator/health` on `embabel-agent-runtime` reports `UP` even when Ollama, LiteLLM or the agent service is
unreachable: those are per-request dependencies it fails closed on, not readiness conditions. See
[Troubleshooting](troubleshooting.md).

## Data and volumes

| Volume / store | Contents |
| --- | --- |
| PostgreSQL (`postgres` service) | Keycloak realm, `openjcockpit` (ai-control: projects, credentials, spec queue, jobs), `embabel` (agent runs, outputs, agent bindings, A2A services), `litellm` (keys, usage). |
| `workflow-definitions` | Workflow and workflow-group YAML, plus the `decision-logs` (OPA policy audit) and `approval-decisions` (approval-gate audit) trails. |
| `workflow-workspaces` | Workflow container workspaces. |
| `git-mcp-workspaces` | Git workspace clones, including per-run/per-job directories. |

### `docker compose down -v` remains destructive

`docker compose down -v` deletes the `workflow-definitions` volume (and any
other named volumes) and remains a destructive action requiring explicit
operator permission before use. Nothing relaxes that rule.

`docker compose stop`/`start` and a plain `docker compose down` (without
`-v`) preserve the LiteLLM `ricky` admin account, the virtual key, and
usage history in the `litellm` Postgres database. `docker compose down -v`
additionally destroys the LiteLLM database state along with Keycloak state,
since the `postgres` service declares no separately-scoped named volume for
litellm data.

For a scoped reset of only the workflow and workflow-group seed data, use
`./scripts/stack-clean-start.sh`. It clears only depth-1 `*.yaml` files in `workflows/` and
`workflow-groups/` inside the `workflow-definitions` volume, then rebuilds and restarts
`embabel-agent-service` so the seed bundle is re-imported. It never removes the volume and never touches
`agents/`, `subagents/`, `skills/`, `decision-logs/`, `approval-decisions/`, the workspaces, Postgres or
Keycloak.

### Local, non-Docker runs use a different storage path

`WorkflowDefinitionProperties` defaults its storage path to
`${java.io.tmpdir}/embabel-workflow-definitions` when not running under
Compose (Compose instead sets `WORKFLOW_DEFINITIONS_PATH` to a container path
backed by the `workflow-definitions` named volume). Consequently, a local, non-Docker run of
`embabel-agent-service` starts from an empty/fresh directory and will not see whatever
records exist in the Compose deployment's volume. This is relevant when trying to reproduce
behaviour seen in the Compose stack by hand.

## Workflow definition file logging

`embabel-agent-service` logs structured events around reading YAML workflow/agent/subagent/skill
definition files and the two file-based audit trails (`decision-logs`, `approval-decisions`), all
of which share the same underlying `YamlDefinitionStore`.

**Log keys and levels** (logger `nl.metafactory.agents.workflow.YamlDefinitionStore`):

| Key                          | Level                                            | Meaning                                                      |
| ----------------------------- | ------------------------------------------------ | ------------------------------------------------------------ |
| `definition.file.read`         | DEBUG                                            | A single definition file was read successfully.               |
| `definition.file.read.failed`  | WARN                                              | A single definition file exists but failed to parse.           |
| `definition.dir.scanned`       | DEBUG when nothing failed, WARN when ≥1 file failed | Summary of a directory listing (scanned/loaded/failed counts). |
| `definition.dir.list.failed`   | WARN                                              | The definitions directory itself could not be listed.         |
| `definition.file.write.failed` | WARN                                              | A definition file could not be written to disk (save failure). |
| `definition.file.delete.failed`| WARN                                              | A definition file could not be deleted from disk (delete failure). |

The WARN-level lines carry the file or directory's **absolute** path, so they are easy to find with
a simple grep even though the corresponding public-facing exception messages only ever carry a
relative file name and a sanitised reason.

**Raising verbosity without a restart.** Two equivalent options:

- Call the already-exposed Spring Boot Actuator endpoint:
  `POST /actuator/loggers/nl.metafactory.agents.workflow.YamlDefinitionStore` with body
  `{"configuredLevel":"DEBUG"}`.
- Set the environment variable `LOGGING_LEVEL_NL_METAFACTORY_AGENTS_WORKFLOW_YAMLDEFINITIONSTORE=DEBUG`
  before starting the service.

**Tolerate-and-skip behaviour change.** A malformed or empty `.yaml` file inside a definitions
directory is now skipped (logged, not returned) instead of failing the entire directory listing
with a 500. This applies to every repository built on the shared store, including the two audit
trails: `decision-logs` (policy decision audit) and `approval-decisions` (approval decision audit).
A corrupt audit record therefore becomes silently absent from an audit listing, visible only in
the server log via `definition.file.read.failed`, never in any API response.

**Downstream impact on `ai-control-service`.** `GET /api/workflows` on `ai-control-service` can now
return a non-2xx `502 Bad Gateway` response carrying the upstream failure message, whereas
previously it always returned `200` with a possibly-empty array. API clients must distinguish a
`502` from a `200` with an empty array: the former means the listing failed, the latter means
there are genuinely no workflows.

**Boot behaviour.** If a seeded workflow id addresses a malformed definition file, the startup
importer that seeds workflows fails deterministically at boot (this component is deliberately
unchanged by this feature). The `definition.file.read.failed` WARN naming the absolute path is
emitted before that failure, and the resulting exception names the relative file, aiding diagnosis.

**A skipped file is a log-only signal.** Its exclusion from a listing is never itself surfaced as
response data to any API client.

See also [`services/embabel-agent-service/README.md`](../../../services/embabel-agent-service/README.md)
for log keys and the workflow-definition write residual-race statement.

## Useful log commands

```bash
docker compose logs -f embabel-agent-service
docker compose logs embabel-agent-service | grep "definition.file.read.failed"
docker compose logs opa            # every policy decision is printed to stdout
docker compose logs local-seed     # one-shot seeding of Keycloak clients and LiteLLM keys
```

## Related

- [Troubleshooting](troubleshooting.md)
- [Known limitations](known-limitations.md)
- [Security considerations](../governance/security-considerations.md)
