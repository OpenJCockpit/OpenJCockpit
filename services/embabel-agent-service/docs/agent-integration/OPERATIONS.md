# Configuratiegedreven agents + A2A — operationele handleiding (SPEC-002)

Deze handleiding is zelfstandig: een ontwikkelaar kan hiermee de lokale demo reproduceren zonder
eerdere context. Voortgang en bewijs per stap: `IMPLEMENTATION_PROGRESS.md`.

## 1. Wat het doet

```
A2A-client ──► LiteLLM A2A Gateway (/a2a/customer-support)
                 │  x-litellm-api-key: Bearer <virtual key>
                 │  x-a2a-customer-support-authorization: Bearer <Keycloak JWT>  → doorgestuurd als `authorization`
                 ▼
embabel-agent-service  POST /a2a/{agentId}  (A2A 0.3, JSON-RPC, alleen message/send)
  ├─ JWT-validatie (bestaande resource server) → tenant uit claim `tenant-claim`
  ├─ AgentDefinitionResolver (1× per run)
  │    PostgreSQL agent_tenant_bindings (tenant ↔ agent, toolallowlist, verplichte guardrails, pins, fallbackrecht)
  │    bron: REMOTE (LiteLLM, UNSUPPORTED in v1.101.2) → LAST_KNOWN_GOOD → LOCAL agents/<id>.md
  │    skills (skills/<id>/SKILL.md), tools (server-registry ∩ allowlist), guardrails (executor verplicht), modelalias
  │    ⇒ immutable ResolvedAgentDefinition + PreparedAgent
  ├─ ConfiguredDefinitionAgent (Embabel @Agent/@Action)
  │    ai.withLlmByRole(<modelalias>) + AgentPromptContributor + native Embabel Skills (per run gematerialiseerd)
  │    + SnapshotBoundTool (call-time snapshot- en OPA-check) + Embabel GuardRails + strikte ToolNotFoundPolicy
  └─ LLM-call via LiteLLM model-gateway (model = alias, bv. customer-chat; policy openjcockpit-agent-pii)
```

Belangrijk: de Embabel-service roept voor zijn modelcalls de LiteLLM **model**-route
(`/v1/chat/completions`) aan, nooit de eigen `a2a/<agent>`-route — er is geen lus.

## 2. Configuratie (`openjcockpit.agent-definitions.*`)

| Property | Env-var | Default | Betekenis |
|---|---|---|---|
| `enabled` | `AGENT_DEFINITIONS_ENABLED` | `false` | Hele feature aan/uit. Uit = geen enkele bean actief. |
| `local-root` | `AGENT_DEFINITIONS_LOCAL_ROOT` | – | Map met `agents/` en `skills/`. Verplicht als `enabled`. |
| `tenant-claim` | `AGENT_DEFINITIONS_TENANT_CLAIM` | `tenant_id` | JWT-claim met tenant-id. Ontbreekt → geweigerd. |
| `source-priority` | – | `REMOTE,LAST_KNOWN_GOOD,LOCAL` | Volgorde van bronnen. |
| `mandatory-guardrails` | – | `[]` | Guardrails die elke agent moet hebben, ongeacht definitie. |
| `fallback.on-unavailable` | `AGENT_DEFINITIONS_FALLBACK_ON_UNAVAILABLE` | `true` | Fallback bij `UNAVAILABLE` (plus per-agent toestemming). |
| `fallback.on-not-found` | `AGENT_DEFINITIONS_FALLBACK_ON_NOT_FOUND` | `false` | Fallback bij `NOT_FOUND` (plus per-agent `fallback_on_not_found`). |
| `cache.ttl` | `AGENT_DEFINITIONS_CACHE_TTL` | `1h` | Geldigheid last-known-good snapshot. |
| `cache.directory` | `AGENT_DEFINITIONS_CACHE_DIR` | – | Map voor snapshots; leeg = geen last-known-good bron. |
| `cache.max-entries` | – | `256` | Maximaal aantal snapshots. |
| `litellm.enabled` | `AGENT_DEFINITIONS_LITELLM_ENABLED` | `false` | LiteLLM als metadatabron + gateway-guardrailverificatie. |
| `litellm.base-url` | `AGENT_DEFINITIONS_LITELLM_BASE_URL` | `http://localhost:4000` | Proxy-root (zonder `/v1`). |
| `litellm.api-key` | `LITELLM_AGENT_REGISTRY_KEY` | – | Key voor `GET /v1/agents` en `POST /policies/resolve`. Nooit in YAML. |
| `litellm.connect-timeout` / `read-timeout` | `…_CONNECT_TIMEOUT` / `…_READ_TIMEOUT` | `3s` / `10s` | |
| `litellm.gateway-guardrails.<id>` | – | – | Guardrail-id → LiteLLM-guardrailnaam; alleen actief na bevestiging via `/policies/resolve`. |
| `litellm.gateway-model-aliases` | – | `[]` | Modelaliassen die als eigen LiteLLM-model worden gerouteerd (Embabel LLM-service per alias). |
| `litellm.model-gateway-url` / `-key` | `LITELLM_BASE_URL` / `LITELLM_VIRTUAL_KEY` | | OpenAI-compatibele gateway voor die aliassen. |
| `a2a.enabled` | `A2A_ENABLED` | `false` | Inkomend A2A-endpoint + Agent Card. Vereist `enabled`. |
| `a2a.public-base-url` | `A2A_PUBLIC_BASE_URL` | – | URL die in de Agent Card staat. |

Elke modelalias in een definitie moet een Embabel-rol zijn: `embabel.models.llms.<alias>=<alias>`
(aliassen met een streepje zijn niet als env-var te zetten — gebruik een profiel-/extra YAML, zie §9).

## 3. Lokale mappen

```
agent-definitions/
  agents/customer-support.md          # YAML-frontmatter (id, version, description, model, skills,
                                      # optionalSkills, tools, guardrails) + Markdown-instructies
  skills/customer-lookup/SKILL.md     # Agent Skills-formaat: name (= mapnaam), description, …
  skills/customer-lookup/references/  # tekstresources (.md/.txt/.json/.yaml/.yml/.csv)
```

Regels: ids kebab-case (max 64); onbekende of dubbele frontmatter-keys, symlinks, `..`, bestanden > 256 KB
en skillpakketten > 1 MB worden geweigerd. `scripts/` wordt nooit gelezen of uitgevoerd.
`allowed-tools` in een skill en tekst in instructies kennen **nooit** tools toe.

## 4. PostgreSQL-mapping

Migratie `src/main/resources/db/migration/V3__create_agent_tenant_bindings.sql` is goedgekeurd
(2026-10-14) en draait met de reguliere Flyway-migraties op elke database.
Lokale seed (alleen docker-compose, via `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed-local`):
`db/seed-local/R__seed_local_agent_bindings.sql` koppelt de 4 geseede agents aan tenant `local`
(model `agent-chat`, guardrail `pii-protection`, geen tools); insert-if-missing, wijzigingen blijven staan.
Let op: is de seed eenmaal toegepast, dan verwacht Flyway de locatie `db/seed-local` bij elke start van die
database (anders faalt validatie op een "missing" repeatable migration).

| Tabel | Sleutel | Inhoud |
|---|---|---|
| `agent_tenant_bindings` | `(tenant_id, agent_id)` | `enabled`, `model_alias` (pin), `pinned_version`, `fallback_allowed`, `fallback_on_not_found` |
| `agent_tenant_binding_tools` | `(tenant_id, agent_id, tool_id)` | toolallowlist |
| `agent_tenant_binding_guardrails` | `(tenant_id, agent_id, guardrail_id)` | extra verplichte guardrails |

Voorbeeld: `docs/agent-integration/demo-seed.sql`.

## 5. Fallbackmatrix

| Remote status | `fallback_allowed` | Config | Resultaat |
|---|---|---|---|
| FOUND | – | – | remote (en opslag als last-known-good) |
| UNSUPPORTED | – | – | volgende bron (bron bestaat niet) |
| UNAVAILABLE | ja | `on-unavailable=true` | last-known-good → lokaal |
| UNAVAILABLE | nee of config uit | – | fout `UNAVAILABLE` |
| NOT_FOUND | ja + `fallback_on_not_found` | `on-not-found=true` | last-known-good → lokaal |
| NOT_FOUND | anders | – | fout `NOT_FOUND` |
| UNAUTHORIZED / INVALID | – | – | altijd fout, nooit fallback |

Last-known-good: verlopen → overslaan + verwijderen; corrupt/hash-mismatch → overslaan + verwijderen.
Elke gekozen definitie doorloopt dezelfde validatie (binding, tools, guardrails, model) tegen de
**actuele** toestand; een snapshot kan dus geen ingetrokken rechten terugbrengen.
Lokale definities zijn fallback voor **configuratie**, niet voor een onbereikbare modelgateway.

## 6. Guardrails

| Id | Executor | Dekking | Fail-closed |
|---|---|---|---|
| `pii-protection` | `PiiProtectionGuardrail` (Embabel `UserInputGuardRail` + `AssistantMessageGuardRail`, CRITICAL) | LLM-input en -output van de run | ja: Embabel gooit `GuardRailViolationException` → A2A-fout `GUARDRAIL_VIOLATION` |
| `<gateway-id>` (config) | `LiteLlmPolicyGuardrailExecutor` | LLM-calls naar de modelalias via LiteLLM | ja: alleen operationeel als `POST /policies/resolve {"model": alias}` de LiteLLM-guardrail in `effective_guardrails` toont |
| tooltoegang | `ToolAccessPolicy` (bind-time) + `SnapshotBoundTool` → `OpaToolCallAuthorizer` (call-time) | elke toolcall | ja; OPA uit = alleen allowlist |

Een guardrail-id zonder operationele executor blokkeert de run (`GUARDRAIL_MISSING`). Een policy-ID
alleen in een prompt of YAML zonder executor telt niet.
LiteLLM-zijde (`infrastructure/litellm/config.yaml`): guardrail `openjcockpit-pii`
(`litellm_content_filter`, `nl_bsn_contextual`/`iban`/`credit_card`, BLOCK, pre+post), policy
`openjcockpit-agent-pii` gekoppeld aan model `customer-chat`; voor A2A-verkeer via
`litellm_params.guardrails` in de agentregistratie.

## 7. A2A-registratie bij LiteLLM

Bestanden: `infrastructure/litellm/a2a/customer-support.agent.yaml` (config) en `.json` (API-payload).
Gepinde protocolversie: **A2A 0.3** (`protocolVersion: "0.3.0"`, transport JSONRPC). Ondersteund:
`message/send` (synchroon, resultaat = `Message`). Niet ondersteund (expliciete fout): `message/stream`
(-32004), `tasks/*` (-32001), push notifications (-32004).

Opties (beide vereisen goedkeuring voor een gedeelde/live instance):
1. Merge het `agents:`-blok in `infrastructure/litellm/config.yaml` en herstart LiteLLM.
2. `curl -X POST $LITELLM_URL/v1/agents -H "Authorization: Bearer $LITELLM_MASTER_KEY" -H 'Content-Type: application/json' -d @infrastructure/litellm/a2a/customer-support.agent.json`

Vereisten LiteLLM: v1.101.2 (gepind in `docker-compose.yml`), a2a-sdk aanwezig in de image, de
client-key moet toegang hebben tot de agent. De caller stuurt zijn Keycloak-JWT als
`x-a2a-customer-support-authorization: Bearer <jwt>`; LiteLLM stuurt die door als `authorization`.

## 8. Healthchecks

- `GET /actuator/health` (publiek) — bestaand; `readiness` = `readinessState,db`.
- `GET /actuator/health/agentDefinitions` — lokale definitie-root leesbaar, aantal agents/skills.
  Bewust niet in readiness: remote-storingen worden door fallback opgevangen.

## 9. Lokale stack: `docker compose up --build`

Eén commando, alles lokaal geseed:

```bash
docker compose up -d --build
./scripts/a2a-roundtrip.sh        # roept de 4 geseede agents aan via de LiteLLM A2A Gateway (:4100)
```

**Eén seed-service** `local-seed` (`infrastructure/local-seed/seed-local.sh`; draait bij elke `up`,
idempotent, geen compose-`--profile` nodig):

| Onderdeel | Script | Resultaat |
|---|---|---|
| Keycloak A2A-client | `keycloak-a2a-client.sh` | confidential client `openjcockpit-a2a` (client_credentials; token bevat `tenant_id=local`), secret `A2A_CLIENT_SECRET` |
| LiteLLM model-key | `litellm-keys.sh` | `embabel-agent-service` (`LITELLM_VIRTUAL_KEY`), incl. `agent-chat`, `customer-chat` |
| **De** LiteLLM A2A-key | `litellm-keys.sh` | `openjcockpit-a2a` (`LITELLM_A2A_KEY`): `allowed_routes: ["/v1/agents","agent_inference_routes"]`, grants voor de 4 agents |
| Opruimen | `litellm-keys.sh` | verwijdert keys van eerdere setups (`embabel-agent-catalog`, `openjcockpit-a2a-client`) |

**Eén Spring-profiel** `local` (`application-local.yml`, door compose geactiveerd met
`SPRING_PROFILES_ACTIVE=local`): Flyway incl. `db/seed-local` (tenant-`local`-bindings), agent-definities +
A2A aan, modelaliassen, agentcatalogus en de A2A-provider. Compose geeft alleen containerpaden en secrets mee.

**Eén A2A-client per A2A-provider.** `openjcockpit.a2a-providers` (lijst; lokaal één entry `type: litellm`)
→ `A2AProviderClients` maakt per provider precies één `A2AProviderClient` (`LiteLlmA2AProviderClient`):
`listAgents()` (catalogus), `agentCard(name)` (Details), `sendMessage(name, text, callerAuthorization)`
(A2A `message/send` via de gateway; de caller-JWT gaat mee als `x-a2a-<agent>-authorization`). Dezelfde
client levert de LiteLLM-verbinding voor gateway-guardrailchecks. Er zijn geen andere LiteLLM/A2A-HTTP-clients
meer; de afzonderlijke `agent-catalog.litellm`- en `agent-definitions.litellm`-verbindingen zijn vervallen
(`agent-definitions.litellm` bevat alleen nog modelgateway-instellingen).

Handmatig aanroepen: token via
`curl -d grant_type=client_credentials -d client_id=openjcockpit-a2a -d client_secret=openjcockpit-a2a-local-secret http://localhost:8080/realms/openjcockpit/protocol/openid-connect/token`
en `POST http://localhost:4100/a2a/<agent>` met `x-litellm-api-key: Bearer sk-openjcockpit-a2a-local` en
`x-a2a-<agent>-authorization: Bearer <token>`.

## 9a. Agentcatalogus in het dashboard (Workflow design → Agents)

`GET /api/agent-catalog` (embabel-agent-service, doorgegeven door ai-control-service) levert:

- **Local** — alle `*.md`-agents onder de repository-map `/agents` (recursief; submappen = hiërarchie).
  Config: `openjcockpit.agent-catalog.local-path` (profiel `local`: `/agents`; compose mount `./agents:/agents:ro`).
- **Eén sectie per A2A-provider-client**, met de providernaam als kop (lokaal **LiteLLM**). De Endpoint-kolom
  toont de gateway-URL voor gebruikers (`LITELLM_PUBLIC_URL`, default `http://localhost:4100`); "routes to"
  is het interne adres waar LiteLLM heen stuurt. **Details** haalt de door LiteLLM gehoste agent card live op.

In de LiteLLM-registratie (`config.yaml`) blijft `url` bewust het docker-interne adres
`http://embabel-agent-service:8081/a2a/<id>` (daarheen forwardt LiteLLM). Config-agents worden bij het
starten van LiteLLM uit `config.yaml` gelezen; na wijzigingen volstaat `docker compose restart litellm`.

### A2A-services beheren (Workflow designer → Configuration)

Het blok **A2A services** toont alle providers met live status en aantal agents (`GET /api/a2a-services`):

- **Uit configuratie** (`openjcockpit.a2a-providers`, lokaal LiteLLM) — read-only; wijzigen of verwijderen geeft 409.
- **Toegevoegd in het dashboard** — LiteLLM-compatibele gateways (`/v1/agents`, `/a2a/<naam>`), opgeslagen in
  tabel `a2a_services` (V6). Velden: id (vast, `a2a:<id>:<agent>` in workflows), naam, base URL, public URL, API key
  en optioneel een caller identity (token-URL, client id, client secret; embabel haalt daarmee een
  client-credentials-token dat de gateway aan de agents doorgeeft). Een wijziging is direct actief in catalogus
  en workflowruns, zonder herstart.
- Secrets worden versleuteld opgeslagen (AES-GCM) met `A2A_SERVICE_ENCRYPTION_SECRET` (min. 16 tekens) en
  `A2A_SERVICE_ENCRYPTION_SALT` (hex); de API geeft ze nooit terug, alleen "set". Zonder sleutel kan niets worden
  opgeslagen (503) en zijn opgeslagen services onbruikbaar (`NOT_CONFIGURED`); een andere sleutel maakt de
  opgeslagen secrets onleesbaar — voer ze dan opnieuw in. Lokaal zet compose een vaste testsleutel.
- Verwijderen kan niet zolang een workflow een agent van de service gebruikt (409 met de workflow-ids).
- Gateway-guardrails en policy-checks gebruiken alleen de LiteLLM-provider uit configuratie.

## 9b. Workflows bestaan uit verwijzingen naar catalogusagents

Een workflow verwijst alleen nog naar agents uit de centrale agentcatalogus (Workflow design → Agents);
de ingebouwde pipeline (requirement … evidence, realisation, spec-publicatie) is verwijderd. De workflow is
de enige uitvoeringsengine: elke run start via een workflow (`POST /api/workflows/{id}/start`) of een
git-workspace-job die een workflow start.

- **Referentie:** `local:<agent-id>` (repository-map `/agents`) of `a2a:<provider>:<agent>` (bijv.
  `a2a:litellm:qa-engineer`). In de API/YAML-DTO heet het veld nog `agentIds`.
- **Koppeltabel** `workflow_agent_refs` (Flyway V4): `workflow_id`, `position` (uitvoervolgorde), `source`
  (LOCAL/A2A), `provider`, `agent_id`. Enige bron van waarheid; workflow-YAML bevat geen agents meer.
  Pre-catalogus-YAML met stage-ids laadt zonder agents en moet in de designer opnieuw gekoppeld worden.
- **Validatie bij opslaan:** alleen geldige referenties, elk maximaal één keer; lokale agent moet geldig in
  `/agents` staan, A2A-agent moet bij een geconfigureerde provider bestaan (bij onbereikbare provider wordt
  het bij het starten opnieuw gecontroleerd). Import controleert alleen de syntax.
- **Uitvoering** (`EmbabelOrchestrator.pipeline`): de agents draaien sequentieel; iedere agent krijgt
  de workflow-input + de outputs van de voorgaande agents (budget `openjcockpit.agent-workflows.max-message-chars`,
  default 30 000; oudste outputs vallen eerst weg). Elke output wordt als run-event vastgelegd (eerste 1 900 tekens).
  - Lokaal: in-process via de configuratiegedreven runtime (tenant-binding, tool-allowlist, guardrails,
    modelalias), definities uit `/agents`. Tenant: `openjcockpit.agent-workflows.tenant` (profiel `local`:
    `local`), anders de klant van de workflow.
  - A2A: via de ene `A2AProviderClient` van de provider (`message/send`), geauthenticeerd met de
    caller-identiteit van de provider (`a2a-providers[].caller-auth`, client credentials; lokaal Keycloak-client
    `openjcockpit-a2a` met `tenant_id=local`).
- **Approval gates en workflow-orbs** verankeren aan een agentreferentie. Accept with comments laat die agent
  opnieuw draaien met de feedback en zijn vorige output (max. 3 iteraties).
- **Dashboard:** de workflow-editor kiest agents per catalogussectie (Local / LiteLLM) en toont de
  uitvoervolgorde met ↑/↓; gate- en orb-dropdowns tonen de gekoppelde agents in die volgorde.
- **Default-workflows** (`default-workflows.json`) koppelen de geseede LiteLLM-agents; ze pushen zelf niet naar
  git; publiceren naar git loopt via git-workspace-jobs (zie hieronder).
- **Volledige outputs** per agent staan in `agent_run_outputs` (Flyway V5, max. 1 MB per agent) en zijn op te
  vragen via `GET /api/agent-runs/{runId}/outputs`.
- **Git-workspace-jobs** (ai-control-service, `POST /api/agentic-workflows/start`) vereisen een `workflowId`.
  Preflight weigert (409) een onbekende workflow (`WORKFLOW_NOT_FOUND`) of een workflow zonder gekoppelde agents
  (`WORKFLOW_HAS_NO_AGENTS`). Kolom `git_workspace_jobs.workflow_id` (ai-control V13). Verloop:
  1. **Clone + branch** in de job-werkruimte `job-<job-id>` van de git-mcp-server, via LiteLLM
     (`git_create_branch`, key `ai-control-service`).
  2. **Workflow** start in embabel met ai-controls eigen service-identiteit (Keycloak-client
     `openjcockpit-ai-control`) en een **git-workspace-ticket** voor ai-control zelf (zie hieronder); embabel
     koppelt de run aan de werkruimte (`WorkflowRunScope.gitWorkspace`).
  3. **Agents** krijgen de git-tools `git.list_files`, `git.read_file` (alle vier) en `git.write_file`
     (software-developer, qa-engineer), via definitie (`tools:`) én tenant-binding (`agent_tenant_binding_tools`).
     De tools gebruiken de repository en werkruimte van de run, nooit LLM-invoer, en gaan via LiteLLM
     (key `embabel-agent-service`). A2A-agents krijgen alleen het run-id mee (`x-a2a-<agent>-x-openjcockpit-workflow-run`);
     de inbound A2A-adapter zoekt de werkruimte op en alleen voor oproepen van `azp=openjcockpit-a2a`.
     Zonder gebonden werkruimte melden de tools dat (starts zonder project, orb-kindworkflows).
  4. **Checkpoint na elke agent** (embabel, `GitWorkspaceCheckpoints`): de volledige output gaat als
     `agent-output/<run-id>/<NN>-<agent>.md` de werkruimte in, daarna `git_commit` ("checkpoint n/N: <agent>")
     en `git_push` van de run-branch, vóórdat de volgende agent start. Een mislukte checkpoint is een run-event,
     geen fout. Uit te zetten met `GIT_WORKSPACE_CHECKPOINTS=false`.
  5. **Eind-commit + push** door ai-control als de run klaar is. "Nothing to commit" is normaal (alles zat al in
     de checkpoints); de branch wordt toch gepusht, zodat een mislukte checkpoint-push hier opnieuw gaat.
     Een run die niet `COMPLETED` eindigt → job `FAILED`, zonder eind-push; de gepushte checkpoints blijven staan.
  De eindstatus (`COMPLETED`/`FAILED`) blijft na cleanup staan (cleanup is een event, geen status).
- **Dashboard-starts op een project draaien in een git-werkruimte** (`ProjectWorkflowRunService`): heeft de start
  een `projectId` met repository, dan
  1. maakt ai-control een job, cloont synchroon (vóór de eerste agent) de repository in `job-<job-id>` en maakt
     branch `<branch-prefix>/<workflow>/<yyyyMMdd-HHmmss>-<job>` (één branch per run; prefix
     `openjcockpit.agentic-workflow.branch-prefix`, default `agentic`);
  2. laat ai-control (service-identiteit) embabel een **ticket** uitgeven voor die werkruimte, deze workflow en de
     `sub` van de gebruiker;
  3. start de run met het **eigen token van de gebruiker** + het ticket: OPA (`workflow.start`, agents) beoordeelt de
     gebruiker en de run staat op diens naam;
  4. volgen checkpoints na elke agent en de eind-push door ai-control (als bij jobs). Alle agents van de run
     delen dezelfde werkruimte: de developer leest wat analist en architect schreven.
  Het startantwoord meldt de branch. Clone mislukt → 502 en geen run; geblokkeerde start → job `FAILED`.
  Starts zonder project (of project zonder repository) draaien zoals voorheen zonder werkruimte.
- **Git-workspace-tickets** (embabel, `POST /internal/git-workspace-tickets`, `GitWorkspaceTickets`): alleen
  `azp=openjcockpit-ai-control` mag ze uitgeven (anders 403). Een ticket hoort bij één werkruimte+branch, één
  workflow en één `sub` (weggelaten → ai-control zelf, voor jobs), is 2 minuten geldig
  (`GIT_WORKSPACE_TICKET_TTL`), en wordt bij de eerste inwisselpoging verbruikt, ook als die niet klopt.
  Start met een ticket dat niet klopt → `BLOCKED`. Max. 1 000 openstaande tickets, alleen in geheugen, nooit gelogd.
  Een door de aanroeper meegestuurd `gitWorkspaceTicket` wordt door ai-control verwijderd. De token-relay in
  ai-control laat een expliciete service-`Authorization` ongemoeid (anders zou een service-aanroep tijdens een
  gebruikersverzoek beide tokens meesturen).
- **Live geverifieerd (2026-10-15, `docker compose up --build`):** clone/branch via LiteLLM in `job-<id>`,
  credentials door ai-control geleverd aan de git-mcp-server, run gebonden aan de werkruimte, agents roepen
  `git_list_files`/`git_read_file` aan zonder policy-weigering. Daarbij opgelost:
  - Keycloak 26 negeerde `KC_HOSTNAME_URL` (hostname v1): tokens die binnen het compose-netwerk werden
    opgehaald hadden `iss=http://keycloak:8080/…` en werden door alle services geweigerd (401 op de agent card,
    dus elke A2A-aanroep via LiteLLM). Nu `KC_HOSTNAME=http://localhost:8080` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`.
  - OPA weigerde elke toolaanroep van configuratiegestuurde agents (geen `workflowId`). Nu draagt **elke**
    workflow-run (ook dashboard-starts zonder git-werkruimte) een `WorkflowRunScope` (workflow-id, run-id,
    optionele git-werkruimte) aan zijn agents over: lokaal direct, A2A via alleen het run-id
    (`x-a2a-<agent>-x-openjcockpit-workflow-run`, opgezocht in `WorkflowRunScopes`, alleen van `azp=openjcockpit-a2a`).
    OPA krijgt `workflowId` en `workflowExecutionId` van de workflow-run. Agent-runs buiten een workflow
    (bijv. een losse A2A-aanroep) hebben geen workflow en blijven geweigerd.
  - Stub-specrepository: `listSpecs("*")` vond niets, jobs vonden dus nooit een spec.
  - Job-poll: `RUN_STATE_LOST` is terminaal; bij een time-out stopt de job ook de run.
  - Lokaal `WORKFLOW_AGENT_TIMEOUT=3600` (vier agents op het lokale model duren >10 min).
  Bestaand en niet opgelost: de job-container-image wordt niet automatisch gepulld
  (`docker pull eclipse-temurin:25-jre`), en een job die loopt tijdens een herstart van ai-control blijft op
  `AGENTS_RUNNING` staan.
- **Gekozen spec als agentinvoer (dashboard-starts):** het dashboard stuurt alleen de bestandsnaam van de
  geselecteerde spec. ai-control (`WorkflowStartEnrichmentService`) leest die spec uit de `specs/`-map van de
  projectrepository (`ProjectSpecService.readSpecContent`) en geeft `# Spec: specs/<naam>` + de inhoud als
  workflow-invoer door. Agents hebben dus geen git-tools of werkruimte nodig om de spec te lezen. Onbekende naam
  of onleesbare repo: de naam gaat ongewijzigd door (start faalt niet). De invoer telt mee in het berichtbudget
  (`max-message-chars`, invoer max. 1/3 = 10 000 tekens bij de default).
- **Git-credentials gaan nooit door LiteLLM of een LLM.** Geen git-tool heeft nog een `username`/`token`-argument.
  De git-mcp-server haalt de credentials van een werkruimte zelf op bij ai-control
  (`GET /internal/git-workspaces/{key}/credentials?repositoryUrl=…`, Keycloak-client `openjcockpit-git-mcp`):
  alleen voor die client (`azp`, anders 403), alleen terwijl de job loopt en alleen voor de projectrepository
  van de job (anders 404); `Cache-Control: no-store`, nooit gelogd. Niet in het publieke contract en niet via
  de dashboard-proxy (`/api/` alleen). Een werkruimte zonder sleutel is anoniem; een sleutel zonder
  credentials faalt dicht.
- **Verwijderd (2026-10-15):** `GET /api/agents` en `POST /api/agent-runs` (embabel én de relay in ai-control,
  contract `AgentDefinitionDto`/`AgentRunRequestDto`), de Requirement…Evidence-agents, subagents, realisation,
  spec-git-publicatie en de config-blokken `openjcockpit.agent-pipeline` en `openjcockpit.spec-git`
  (`SPEC_GIT_*`-env in compose). Blijven: `GET`/`DELETE /api/agent-runs/{runId}` en `…/outputs`.
  `/api/agent-definitions` levert alleen nog opgeslagen definities (`builtIn` is altijd `false`).
- **git-mcp-server** is een aparte MCP-server, geregistreerd bij LiteLLM (`config.yaml` →
  `mcp_servers.openjcockpit_git`, SSE `http://git-mcp-server:8093/sse`, vaste `server_id`,
  `allow_all_keys: false`, alleen privénetwerk) en **niet meer op de host gepubliceerd** (geen eigen
  authenticatie, dus alleen via LiteLLM). `local-seed` geeft `object_permission.mcp_servers: [openjcockpit_git]`
  aan `embabel-agent-service` (agent-tools) en `ai-control-service` (jobs; alleen `/mcp-rest/tools/call|list`,
  geen modellen). De oude, directe MCP-client in embabel (`mcp/*`, `GIT_MCP_SERVER_URL`) staat uit.
- **Git-credentials in `WorkflowStartInput`/`AgentRunRequest`** (`gitUsername`/`gitToken`) blijven bestaan voor
  dashboard-starts; jobs gebruiken ze niet (zie hierboven).
- **Bekende beperkingen werkruimte-runs:** de job volgt de run maximaal `WORKFLOW_AGENT_TIMEOUT` (lokaal 3600 s),
  óók terwijl een approval gate wacht, en stopt de run daarna; tickets en run-scopes zijn in-memory (herstart van
  embabel → lopende binding weg); orb-kindworkflows erven de werkruimte niet.

## 10. Testcommando's

```bash
# alles (vanaf repo-root)
mvn -o -pl services/embabel-agent-service -am test
# alleen SPEC-002
mvn -o -pl services/embabel-agent-service -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='nl.metafactory.agents.definition.**.*Test,nl.metafactory.agents.a2a.*Test'
# live roundtrip (vereist draaiende stack, zie §9)
LITELLM_KEY=… A2A_JWT=… ./scripts/a2a-roundtrip.sh
```

## 11. Beperkingen

- LiteLLM v1.101.2 heeft geen API voor agent-Markdown of skillpakketinhoud: remote content = `UNSUPPORTED`; lokale bron levert content.
- A2A: alleen 0.3, alleen `message/send`, geen streaming/tasks/push; geen authenticated extended card.
- Tenantclaim bestaat alleen voor de lokale service client `openjcockpit-a2a` (hard-coded `tenant_id=local`); voor echte gebruikers/tenants is nog een Keycloak-mapper per tenant nodig. Een gewijzigde tenantwaarde in een bestaande mapper wordt door de seed niet overschreven.
- `logging.level.com.embabel.agent: DEBUG` (bestaande instelling) kan promptinhoud loggen; aanbevolen `INFO` in productie (niet gewijzigd: raakt bestaand gedrag).
- `LiteLlmPolicyGuardrailExecutor` bewijst attachment-configuratie via `/policies/resolve`, niet de daadwerkelijke uitvoering per call; of de policy-engine in de gebruikte LiteLLM-editie beschikbaar is, is niet live geverifieerd.
- Live roundtrip LiteLLM → Embabel → LiteLLM en de lokale demo zijn niet uitgevoerd in deze omgeving (`NOT_RUN_EXTERNAL`).
