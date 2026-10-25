# Running the agent runtime on another host

`embabel-agent-runtime` needs no shared filesystem, database or Docker network with the rest of the stack:
every dependency is a URL plus a credential. Moving it to another physical or virtual host is a
configuration change.

## What the runtime needs to reach

| Dependency | Setting (runtime) | Credential |
| --- | --- | --- |
| `embabel-agent-service` (bindings, run scopes, tool decisions) | `EMBABEL_AGENT_SERVICE_BASE_URL` | Keycloak client `openjcockpit-agent-runtime` (`AGENT_RUNTIME_TOKEN_URL`, `AGENT_RUNTIME_CLIENT_ID`, `AGENT_RUNTIME_CLIENT_SECRET`) |
| Keycloak (token validation) | `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_ISSUER` | — |
| LiteLLM (models, git MCP) | `LITELLM_BASE_URL` (with `/v1`), `GIT_WORKSPACE_GATEWAY_BASE_URL` / `LITELLM_PROXY_URL` | `LITELLM_VIRTUAL_KEY` (models + git MCP server) |
| OpenAI (optional roles) | — | `OPENAI_API_KEY` |
| Ollama (only with `LLM_CODING_ROUTE=direct`) | `OLLAMA_BASE_URL` | — |

And who needs to reach the runtime:

| Caller | Setting | Notes |
| --- | --- | --- |
| `embabel-agent-service` | `EMBABEL_AGENT_RUNTIME_BASE_URL` (+ its client `openjcockpit-agent-service`) | runs `local:` agents and drafts |
| LiteLLM A2A gateway | `agents[].agent_card_params.url` in `infrastructure/litellm/config.yaml` | must equal `A2A_PUBLIC_BASE_URL/a2a/<agent>` |
| A2A clients | `A2A_PUBLIC_BASE_URL` (advertised in Agent Cards) | runtime's own public address |

## Steps

1. **Run the image** on the target host:

   ```bash
   docker run -d --name embabel-agent-runtime -p 8082:8082 \
     -e SPRING_PROFILES_ACTIVE=local \
     -e EMBABEL_AGENT_SERVICE_BASE_URL=https://agent-service.example.com \
     -e AGENT_RUNTIME_TOKEN_URL=https://sso.example.com/realms/openjcockpit/protocol/openid-connect/token \
     -e AGENT_RUNTIME_CLIENT_SECRET=<from your secret store> \
     -e KEYCLOAK_JWK_SET_URI=https://sso.example.com/realms/openjcockpit/protocol/openid-connect/certs \
     -e KEYCLOAK_ISSUER=https://sso.example.com/realms/openjcockpit \
     -e LITELLM_BASE_URL=https://llm-gateway.example.com/v1 \
     -e LITELLM_PROXY_URL=https://llm-gateway.example.com \
     -e LITELLM_VIRTUAL_KEY=<from your secret store> \
     -e A2A_PUBLIC_BASE_URL=https://agents.example.com \
     openjcockpit/embabel-agent-runtime:<tag>
   ```

   The `local` profile only adds defaults (seeded agent aliases, A2A enabled); every URL above overrides
   it. Without the profile set `AGENT_DEFINITIONS_ENABLED=true`, `A2A_ENABLED=true` and the model aliases
   explicitly.

2. **Terminate TLS** in front of the runtime (reverse proxy or load balancer) at `https://agents.example.com`.
   Expose `/a2a/**`, `/internal/v1/**` and `/actuator/health/**` only; the runtime authenticates every
   request itself, but nothing else needs to be public.

3. **Point the agent service at it**: `EMBABEL_AGENT_RUNTIME_BASE_URL=https://agents.example.com`, and make
   the agent service reachable from the runtime host at `EMBABEL_AGENT_SERVICE_BASE_URL` (only
   `/internal/agent-runtime/v1/**` is needed by the runtime).

4. **Re-point LiteLLM**: change the `url` of each runtime-served agent in
   `infrastructure/litellm/config.yaml` to `https://agents.example.com/a2a/<agent>` and restart LiteLLM.

5. **Keycloak**: the issuer the services validate (`KEYCLOAK_ISSUER`) must equal the `iss` of tokens from
   the token URL each side uses. Create the confidential clients `openjcockpit-agent-service` and
   `openjcockpit-agent-runtime` (client credentials only) with real secrets.

6. **Definitions**: the seeded definitions ship in the image. To use your own, mount a directory read-only
   at `/agent-definitions` (or bake a derived image). `local:` catalogue agents need nothing on the runtime
   host — their definitions travel with each request.

7. **Verify**: `curl https://agents.example.com/actuator/health/readiness` returns `UP`; start a workflow
   with a `local:` agent and an `a2a:litellm:` agent and check both run events.

## Network and timeouts

- Agent executions are long synchronous requests (minutes). Proxies between the agent service and the
  runtime need a read timeout at least `AGENT_RUNTIME_EXECUTION_TIMEOUT` (default 60m); LiteLLM's
  `request_timeout` bounds A2A calls.
- Proxies must **not** retry `POST /internal/v1/agent-executions`; the runtime rejects a retried execution id
  (`409`) anyway, so a retry can only produce an error, never a second run.
- Scale-out: the execution registry is per instance; use sticky routing by execution id if you run several
  runtime replicas and rely on the status endpoint.

## Not yet covered

- Streaming / asynchronous (202 + polling) execution; cancellation of an agent that is already running.
- Mutual TLS between the services (OAuth2 client credentials over HTTPS is the current model).
- Automated distribution of the LiteLLM agent URLs (step 4 is manual).
