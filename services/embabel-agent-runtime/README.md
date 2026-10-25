# embabel-agent-runtime

The **execution plane** of OpenJCockpit: a standalone Spring Boot application and Docker image
(`openjcockpit/embabel-agent-runtime`) that runs configuration-driven agents on the
[Embabel](https://github.com/embabel/embabel-agent) agent platform.

It owns:

- Embabel initialisation (`@EnableAgents`) and `ConfiguredDefinitionAgent`;
- definition resolution (`definition/**`): served definitions and skills in `agent-definitions/` (shipped in
  the image at `/agent-definitions`), last-known-good cache, catalogue snapshots sent by the agent service;
- LLM routes (`config/**`: LiteLLM gateway, direct Ollama, OpenAI, agent model aliases) and definition drafts;
- tools (`ToolRegistry`, bind-time allowlist, agent git tools via LiteLLM) and guardrails;
- inbound A2A (`/a2a/{agentId}`, Agent Cards) and the internal execution API (`/internal/v1/**`) used by
  `embabel-agent-service`;
- the in-memory execution registry (one run per execution id, status endpoint).

It has **no database**. Tenant bindings, the scope of active workflow runs and tool-call policy decisions
are read from `embabel-agent-service` (`EMBABEL_AGENT_SERVICE_BASE_URL`) with the runtime's own Keycloak
client (`openjcockpit-agent-runtime`). It depends on `service-common` only, never on the agent service.

```bash
mvn -pl services/embabel-agent-runtime -am verify          # tests + 100 % line coverage gate
docker build -f services/embabel-agent-runtime/Dockerfile -t openjcockpit/embabel-agent-runtime:local .
docker compose up embabel-agent-runtime                     # port 8092 on the host
```

Docs: [Agent runtime](../../docs/readme/concepts/agent-runtime.md) ·
[Remote agent runtime](../../docs/readme/operations/remote-agent-runtime.md) ·
[Configuration reference](../../docs/readme/reference/configuration-reference.md#agent-service--agent-runtime).
