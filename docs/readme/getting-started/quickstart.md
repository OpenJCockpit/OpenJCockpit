# Quickstart

Run the complete OpenJCockpit stack locally with Docker Compose, log in to the AI Orchestration
Dashboard and run your first agent workflow.

> **Local development stack.** Everything below starts a local, single-machine development
> environment with seeded, publicly known credentials. It is not a production deployment. Read
> [Security considerations](../governance/security-considerations.md) before exposing any part of
> it beyond your own machine.

## 1. Prerequisites

| Requirement | Why | Notes |
| --- | --- | --- |
| Docker with Docker Compose | Runs the stack (Keycloak, PostgreSQL, LiteLLM, OPA, the Java services and the dashboard). | Docker Desktop on macOS/Windows; Docker Engine on Linux (see the Linux warning in [LLM providers](../integrations/llm-providers.md#local-ollama-qwen-model-prerequisite-before-you-start-the-stack)). |
| [Ollama](https://ollama.com) on the **host** machine | Serves the default/coding LLM for every agent. Ollama is **not** part of the Compose stack. | Run `ollama pull qwen3.6:27b` before starting, and keep the daemon running while the stack is up. This is a large model; make sure your machine can run it. |
| An OpenAI API key | `OPENAI_API_KEY` is **mandatory to boot the stack**. The `best`/`cheapest` LLM aliases and embeddings remain OpenAI-hosted. | Put it in `.env`. |
| Free host ports | `4000` (dashboard), `4100` (LiteLLM), `8080` (Keycloak), `8091` (`embabel-agent-service`), `9080` (`ai-control-service`), `8181` (OPA). | |
| A Git repository and a GitHub token (optional for a first look) | Needed to run workflows against a real project repository. | See [Git integration](../workflows/git-integration.md) and the token scopes in [Spec queue](../workflows/spec-queue.md#github-token-scopes). |

For building and testing from source (JDK 25, Maven 3.9+, Node.js 22) see
[Local development](../development/local-development.md).

## 2. Configure and start

```bash
cp .env.example .env
# edit .env and add your OPENAI_API_KEY
docker compose up --build
```

All other variables in `.env.example` are commented out and have working local defaults. The
helper script `./scripts/stack-start.sh` does the same build-and-start (idempotent) and also supports
`--infra-only` and `--with-local-marketplace`; run it with `--help` for details.

On every `docker compose up`, one-shot seed containers prepare the local environment:
Keycloak realm roles, local users and the dedicated e2e user, the LiteLLM virtual keys, and the
Keycloak service clients used between services (`local-seed`). They are idempotent.

> **Health is not readiness for inference.** `/actuator/health` reports `UP` even when Ollama is
> unreachable. The first sign of a missing model is an LLM call failing at run time. See
> [Troubleshooting](../operations/troubleshooting.md).

## 3. Open the dashboard

| URL | What |
| --- | --- |
| http://localhost:4000 | AI Orchestration Dashboard |
| http://localhost:8080/admin | Keycloak admin console |
| http://localhost:4100 | LiteLLM gateway admin UI (separate, non-Keycloak login) |

The app sends you automatically to the custom OpenJCockpit login page of Keycloak. After a valid
login, you are redirected to the landing hub. Log in with one of the seeded local users (for
example `tony` / `Welkom01!`, who also has the `openjcockpit-admin` role). All seeded users and
passwords are listed in [Authentication](authentication.md). They are **local-only** credentials.

## 4. Your first workflow run

The repository's documentation site has an illustrated tutorial
(["Tutorial: your first project and workflow run"](https://openjcockpit.github.io/OpenJCockpit/)).
In short:

1. **Add your project** in the dashboard, with its Git repository URL, and add a Git credential
   under *Settings → Git credentials*.
2. **Initialize the spec structure.** If the repository has no `.specify` folder on its base
   branch, the dashboard offers the spec-init flow, which pushes a starter template branch for review.
   See [Spec-driven development](../workflows/spec-driven-development.md).
3. **Create your first spec** with **✨ Create spec with prompt**, which starts the *Create spec
   from prompt (RAG)* workflow.
4. **Run a workflow** on the selected spec, for example *Implement spec* (Solution Architect →
   Software Developer → QA Engineer) or *Realise spec (code)* (Business Analyst first, then the
   same chain). Follow the agents' progress and outputs in the dashboard, and add an approval gate
   to a workflow if you want a human decision before it continues.
5. **Review and merge.** Workflow runs on a project work on their own Git branch. Review the
   branch, open or merge the pull request through your normal review process.

What each default workflow does, and what it does *not* do, is described in
[Agents and workflows](../workflows/agents-and-workflows.md#default-workflows-automatically-imported).

> **First workflow container run.** Git-workspace jobs use the `eclipse-temurin:25-jre` container
> image, which is not pulled automatically. If a job fails to start its container, run
> `docker pull eclipse-temurin:25-jre` once.

## 5. Stop, restart and reset

| Command | Effect |
| --- | --- |
| `docker compose stop` / `docker compose start`, or `./scripts/stack-stop.sh` | Stops and starts containers. All state is preserved. |
| `docker compose down` (without `-v`) | Removes containers. Named volumes (Keycloak, databases, LiteLLM, workflow definitions, workspaces) are preserved. |
| `./scripts/stack-clean-start.sh` | Scoped reset of **only** the workflow and workflow-group seed data, then re-imports the default workflows. Asks for typed confirmation. |
| `docker compose down -v` | **Destructive.** Deletes all named volumes: the Keycloak realm, all databases, LiteLLM keys and usage history, workflow definitions and audit trails, and Git workspaces. Use it only deliberately. See [Re-importing the realm](authentication.md#re-importing-the-realm). |

## Next steps

- [Authentication](authentication.md): seeded users, roles and the custom login theme.
- [LLM providers](../integrations/llm-providers.md): Ollama, the LiteLLM gateway, OpenAI and how to revert.
- [Agents and workflows](../workflows/agents-and-workflows.md): what runs when you press *Start*.
- [Configuration reference](../reference/configuration-reference.md): every environment variable.
- [Known limitations](../operations/known-limitations.md): read this before relying on the stack.
