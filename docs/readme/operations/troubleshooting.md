# Troubleshooting

Common problems with the local stack, collected from the topic documentation. Each entry gives the
cause and the fix, and links to the page with the full explanation.

**Jump to:** [Start-up](#start-up-and-health) · [LLM and gateway](#llm-and-gateway) ·
[Login](#login-and-identity) · [Workflows and policy](#workflows-and-policy) ·
[Spec queue](#spec-queue) · [Agent runtime](#agent-runtime-and-a2a) ·
[Runs and git workspaces](#runs-and-git-workspaces) · [Workflow definitions](#workflow-definitions) ·
[Local development](#local-development)

## Start-up and health

### The stack does not start

`ai-control-service` or `embabel-agent-runtime` fails at boot.

- **Cause:** `OPENAI_API_KEY` is missing in `.env`. It is mandatory to boot.
- **Fix:** `cp .env.example .env`, then set `OPENAI_API_KEY`.
- **More:** [Quickstart](../getting-started/quickstart.md)

## LLM and gateway

### Health is `UP`, but every agent run fails on the first LLM call

- **Cause:** Ollama is not running, the model is not pulled, or it is not reachable from the containers.
  Health does not probe Ollama.
- **Fix:** check `ollama list` for `qwen3.6:27b` and keep the daemon running. On Linux, see the networking warning.
- **More:** [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack)

### Agent runs fail after setting `OLLAMA_BASE_URL=http://localhost:11434` in `.env`

- **Cause:** `.env` applies to the container, whose loopback has no Ollama.
- **Fix:** remove the override, or point it at an address reachable from the container.
- **More:** [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack)

### LLM calls fail with `401` from the gateway

You see two requests per call.

- **Cause:** `LITELLM_VIRTUAL_KEY` is wrong or missing, or `local-seed` did not run.
- **Fix:** check `docker compose logs local-seed`. This is expected fail-closed behaviour.
- **More:** [Known limitations](known-limitations.md#llm-and-gateway-limitations)

### The LiteLLM gateway is down and you need agents to work

- **Cause:** gateway outage.
- **Fix:** apply revert B. Set `LLM_CODING_ROUTE=direct` in `.env`, then run
  `docker compose up -d embabel-agent-runtime`.
- **More:** [LLM providers](../integrations/llm-providers.md#reverting-the-default-llm)

### Runs take very long or time out

- **Cause:** several agents share a large local model. The worst case is 600 s per LLM call.
- **Fix:** none needed locally. Jobs wait up to `WORKFLOW_AGENT_TIMEOUT` (3600 s).
- **More:** [LLM providers](../integrations/llm-providers.md#litellm-gateway)

## Login and identity

### Login redirects to `:3000`, or shows "invalid redirect URI"

- **Cause:** the Keycloak database still holds the old realm from before the landing app was removed.
- **Fix:** re-import the realm, or edit the `openjcockpit` client.
- **More:** [Authentication](../getting-started/authentication.md#re-importing-the-realm)

## Workflows and policy

### A workflow start returns `BLOCKED`

- **Cause:** no agents are linked, all agents are denied by policy, or the git-workspace ticket is invalid.
- **Fix:** read the reason in the response and check `docker compose logs opa`.
- **More:** [Agents and workflows](../workflows/agents-and-workflows.md#refusals-at-the-write-and-start-boundaries)

### Agent tool calls are denied by policy

- **Cause:** `workflowId` is missing in the policy input, for example for an agent run outside a workflow.
- **Fix:** start agents through a workflow.
- **More:** [Policy enforcement](../governance/policy-enforcement.md#the-default-policy-is-a-starting-point)

### The dashboard shows "run state lost"

- **Cause:** the run id is unknown or expired, or the run was executing an agent when `embabel-agent-service`
  restarted. Runs paused at a gate survive a restart.
- **Fix:** start the workflow again.
- **More:** [Approval gates](../governance/approval-gates.md#paused-runs-survive-a-restart-or-redeploy)

## Spec queue

### A manual workflow start returns `409 SPEC_QUEUE_ITEM_ACTIVE`

- **Cause:** a spec queue item is active for this project.
- **Fix:** wait, or pause or cancel the queue item.
- **More:** [Spec queue](../workflows/spec-queue.md#how-to-use-it)

### The spec queue halted

- **Cause:** an item failed, for example with `START_OUTCOME_UNKNOWN` or `MERGE_WAIT_TIMEOUT`.
- **Fix:** retry, skip or remove the item, then resume the queue. Check the workflow executions for a
  still-running run.
- **More:** [Spec queue](../workflows/spec-queue.md#residual-risks)

## Agent runtime and A2A

### A workflow run fails with "The agent runtime is unavailable" or "not configured"

The full message reads `Local agent … failed: …`.

- **Cause:** `embabel-agent-runtime` is down, unreachable at `EMBABEL_AGENT_RUNTIME_BASE_URL`, or its
  Keycloak client is missing.
- **Fix:**

  ```bash
  docker compose ps embabel-agent-runtime
  curl localhost:8092/actuator/health/readiness
  ```

  Then check `AGENT_SERVICE_CLIENT_SECRET` and that `local-seed` completed.
- **More:** [Agent runtime](../concepts/agent-runtime.md)

### Agents answer `TENANT_FORBIDDEN` or `UNAVAILABLE`, and tool calls are denied

The log says "policy decision unavailable".

- **Cause:** the runtime cannot reach `embabel-agent-service` (`EMBABEL_AGENT_SERVICE_BASE_URL`) or cannot
  authenticate as `openjcockpit-agent-runtime`. It fails closed.
- **Fix:**

  ```bash
  docker compose logs embabel-agent-runtime | grep agent-service.call.failed
  ```

  Then check `AGENT_RUNTIME_CLIENT_SECRET`.

### A2A calls through LiteLLM fail with a connection error after the split

- **Cause:** LiteLLM still routes to `embabel-agent-service:8081`.
- **Fix:** set the agent `url`s in `infrastructure/litellm/config.yaml` to
  `http://embabel-agent-runtime:8082/a2a/<agent>`, then restart LiteLLM.

## Runs and git workspaces

### A git-workspace job fails to start its container

- **Cause:** the job image is not pulled automatically.
- **Fix:** `docker pull eclipse-temurin:25-jre`
- **More:** [Git integration](../workflows/git-integration.md#known-limitations-of-workspace-runs)

### A dashboard start on a project returns `502`

- **Cause:** cloning the project repository failed because of the URL or the credential.
- **Fix:** check the project URL and *Settings → Git credentials*.
- **More:** [Git integration](../workflows/git-integration.md#dashboard-starts-on-a-project-run-in-a-git-workspace)

## Workflow definitions

### A workflow is missing from the list, or a policy or approval audit record is missing

- **Cause:** a malformed YAML file was skipped.
- **Fix:**

  ```bash
  docker compose logs embabel-agent-service | grep definition.file.read.failed
  ```

- **More:** [Runbooks](runbooks.md#workflow-definition-file-logging)

### `GET /api/workflows` returns `502`

- **Cause:** the upstream listing in `embabel-agent-service` failed. This differs from an empty `200`.
- **Fix:** see the service log.
- **More:** [Runbooks](runbooks.md#workflow-definition-file-logging)

### `embabel-agent-service` fails at boot after a rollback

- **Cause:** older code cannot parse `approvalGate:` in saved workflows, or a seeded workflow file is malformed.
- **More:** [Approval gates rollback hazard](../governance/approval-gates.md#rollback-hazard-removing-a-saved-approvalgate-configuration-before-rolling-back-past-this-feature),
  [Runbooks](runbooks.md#workflow-definition-file-logging)

### Old workflows load without agents

- **Cause:** pre-catalogue workflow YAML with old stage ids.
- **Fix:** re-link agents in Workflow Design.
- **More:** [Agents and workflows](../workflows/agents-and-workflows.md#export-and-import)

### Marketplace requests go to `/skills/skills`

- **Cause:** the stored marketplace URL already ends in `/skills`.
- **Fix:** register the base URL.
- **More:** [Skills Hub](../integrations/skills-marketplace.md#known-limitation-double-skills-if-the-stored-url-already-ends-in-skills)

## Local development

### Port `4000` is already in use when running E2E or Vite

- **Cause:** the Vite dev server and the containerised dashboard both bind `4000` in E2E modes.
- **Fix:** use one E2E mode at a time.
- **More:** [Testing](../development/testing.md)

### A local, non-Docker `embabel-agent-service` does not show your workflows

- **Cause:** it uses `${java.io.tmpdir}/embabel-workflow-definitions`, not the Compose volume.
- **Fix:** set `WORKFLOW_DEFINITIONS_PATH`.
- **More:** [Runbooks](runbooks.md#local-non-docker-runs-use-a-different-storage-path)

---

Still stuck? Check the logs (see [Runbooks](runbooks.md#useful-log-commands)) and open an issue on
[GitHub](https://github.com/OpenJCockpit/OpenJCockpit/issues). Report security problems privately via
the [security policy](../../../SECURITY.md).
