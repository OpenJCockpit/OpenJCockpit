# Agent integration — implementation progress

| Step | Status | Changed files | Tests | Evidence/blocker |
|---|---|---|---|---|
| 01 Repository baseline | DONE | `docs/agent-integration/IMPLEMENTATION_PROGRESS.md` | `mvn -o -pl services/embabel-agent-service -am test` | 948 tests, 0 failures, 0 errors, 0 skipped (2026-10-14) |
| 02 Configuration binding | DONE | `src/main/java/nl/metafactory/agents/definition/config/AgentDefinitionProperties.java`, `src/main/resources/application.yml` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentDefinitionPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false` | 7 tests: fail-safe defaults, valid binding; missing local-root / api-key / public-base-url and duplicate source priority make startup fail; api-key never in toString |
| 03 Immutable resolved model | DONE | `src/main/java/nl/metafactory/agents/definition/model/*` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ResolvedAgentDefinitionTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 tests: defensive copies, unmodifiable, guardrail without executor refused, invalid hash/blank id refused, toString without instructions |
| 04 Frontmatter parser | DONE | `src/main/java/nl/metafactory/agents/definition/markdown/*`, `src/test/resources/agent-definitions/**` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentMarkdownParserTest -Dsurefire.failIfNoSpecifiedTests=false` | 10 tests: fixture parsed; missing/unterminated frontmatter, unknown or duplicate keys, path-like ids, empty body, >256KB refused; error message echoes no content |
| 05 Local agent loader | DONE | `src/main/java/nl/metafactory/agents/definition/source/AgentDefinitionProvider.java`, `ConfinedRoot.java`, `LocalMarkdownAgentDefinitionProvider.java`, `src/main/java/nl/metafactory/agents/definition/model/ProviderResult.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LocalMarkdownAgentDefinitionProviderTest -Dsurefire.failIfNoSpecifiedTests=false` | 9 tests: FOUND, NOT_FOUND, traversal ids → INVALID, symlinked file and symlinked directory → INVALID, id ≠ file name → INVALID |
| 06 Local skill inventory | DONE | `src/main/java/nl/metafactory/agents/definition/skill/SkillPackage.java`, `SkillMarkdownParser.java`, `SkillPackageProvider.java`, `LocalSkillInventory.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LocalSkillInventoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 5 tests: only conforming directories (name = directory name, description, allowed keys); symlinks refused; `scripts/` only listed, never loaded |
| 07 Skill reference validation | DONE | `src/main/java/nl/metafactory/agents/definition/skill/SkillResolver.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=SkillResolverTest -Dsurefire.failIfNoSpecifiedTests=false` | 7 tests: missing mandatory skill blocks; optional NOT_FOUND omitted+reported; optional INVALID/UNAVAILABLE blocks; UNAUTHORIZED does not fall back |
| 08 Tool allowlist | DONE | `src/main/java/nl/metafactory/agents/definition/tool/RegisteredAgentTool.java`, `ToolRegistry.java`, `ToolAccessPolicy.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ToolAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 tests: unknown tool, non-allowlisted tool and missing allowlist → TOOL_DENIED; duplicate ids/name clash refused |
| 09 Guardrail contract | DONE | `src/main/java/nl/metafactory/agents/definition/guardrail/*`, `AgentDefinitionProperties` (`mandatory-guardrails`) | `mvn -o -pl services/embabel-agent-service -am test -Dtest=GuardrailPolicyEnforcerTest -Dsurefire.failIfNoSpecifiedTests=false` | 7 tests: missing or non-operational executor → GUARDRAIL_MISSING; mandatory guardrail cannot be bypassed; PII guard yields CRITICAL (Embabel 1.5.1 only aborts on CRITICAL) |
| 10 PromptContributor | DONE | `src/main/java/nl/metafactory/agents/definition/runtime/AgentPromptContributor.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentPromptContributorTest -Dsurefire.failIfNoSpecifiedTests=false` | 3 tests: deterministic output; the only field is a final String, the only constructor parameter the snapshot (no I/O possible) |
| 11 Native Skills | DONE | `pom.xml` (+`embabel-agent-skills:${embabel-agent.version}` = 1.5.1), `src/main/java/nl/metafactory/agents/definition/runtime/MaterializedSkills.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=MaterializedSkillsTest -Dsurefire.failIfNoSpecifiedTests=false` | 2 tests: only selected skills visible (`Skills.withLocalSkill` per skill, no shared root); scripts not present/readable; `NoOpExecutionEngine`; per-run directory deleted afterwards |
| 12 Local Embabel run | DONE (live: NOT_RUN_EXTERNAL) | `src/main/java/nl/metafactory/agents/definition/runtime/ConfiguredDefinitionAgent.java`, `PreparedAgent.java`, `SnapshotBoundTool.java`, `ToolCallAuthorizer.java`, `OpaToolCallAuthorizer.java`, `StrictToolNotFoundPolicy.java`, `RunContext.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ConfiguredDefinitionAgentTest -Dsurefire.failIfNoSpecifiedTests=false` | 3 tests with mocked Embabel `Ai`/`PromptRunner`: `withLlmByRole(alias)`, contributor, skill references, only bound tools, guardrails, strict ToolNotFoundPolicy; a policy deny never reaches the tool. Live sample run not performed: LiteLLM/Postgres/Keycloak are not running locally |
| 13 PostgreSQL mapping investigation | DONE | this document (step 13 section) | – | No agent/tenant schema present; recorded without assumed DDL |
| 14 PostgreSQL configuration repository | DONE | `src/main/resources/db/migration/V3__create_agent_tenant_bindings.sql` (originally a test-only location; moved after approval B3), `src/main/java/nl/metafactory/agents/definition/binding/*`, `src/test/resources/application.yml` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentTenantBindingRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 5 tests (H2 PostgreSQL mode): migration applied once; exactly one binding per tenant; no cross-tenant leakage; PK prevents duplicates. Part of the regular migrations since B3 |
| 15 Resolver composition | DONE | `src/main/java/nl/metafactory/agents/definition/resolver/AgentDefinitionResolver.java`, `ModelAliasCatalog.java`, `DefinitionSelection.java`, `DefinitionSourceSelector.java`, `SourceChainSelector.java`, `FallbackPolicy.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentDefinitionResolverTest -Dsurefire.failIfNoSpecifiedTests=false` | 9 tests: concrete config resolved; missing/disabled binding, tool not granted, version/model pin, unknown model alias, guardrail without executor, unknown agent, traversal id fail with the correct classification |
| 16 Run context and trace | DONE | `src/main/java/nl/metafactory/agents/definition/runtime/AgentRunService.java`, `TraceIds.java`, `RunContext.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 4 tests: one resolution per run despite a source change during the run; W3C `traceparent` adopted; MDC only during the run; log injection in ids refused |
| 17 LiteLLM connectivity | DONE | `src/main/java/nl/metafactory/agents/definition/litellm/LiteLlmHttpClient.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LiteLlmHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false` | 8 mock-server tests: success+Bearer, path encoding, 401/403→UNAUTHORIZED, 404→NOT_FOUND, 5xx/timeout/disconnect→UNAVAILABLE, no JSON→INVALID, no redirects |
| 18 Reading the A2A Agent Registry | DONE | `src/main/java/nl/metafactory/agents/definition/litellm/LiteLlmAgentMetadataClient.java`, `AgentCardMetadata.java`, `src/test/resources/litellm/v1-agents-list.json` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LiteLlmAgentMetadataClientTest -Dsurefire.failIfNoSpecifiedTests=false` | 5 contract tests against `list[AgentResponse]` (verified in LiteLLM v1.101.2 `proxy/agent_endpoints/endpoints.py`, `types/agents.py`); metadata contains no prompt fields |
| 19 External agent content | DONE — `UNSUPPORTED` | `src/main/java/nl/metafactory/agents/definition/litellm/ExternalAgentContentProvider.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ExternalContentProvidersTest -Dsurefire.failIfNoSpecifiedTests=false` | LiteLLM v1.101.2 `AgentResponse` only has `agent_card_params`, `litellm_params` (redacted) and limits; no instruction/Markdown field or content route. The adapter always returns UNSUPPORTED; the local source keeps working |
| 20 External skills | DONE — `UNSUPPORTED` | `src/main/java/nl/metafactory/agents/definition/litellm/ExternalSkillPackageProvider.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ExternalContentProvidersTest -Dsurefire.failIfNoSpecifiedTests=false` | LiteLLM v1.101.2 `/v1/skills[/{id}]` response model `Skill` contains no `file_content`; the repo marketplace mock is name/description only. No archive downloaded/extracted; local skills remain usable (test) |
| 21 External precedence | DONE | `src/main/java/nl/metafactory/agents/definition/resolver/SourceOrder.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=ExternalPrecedenceTest -Dsurefire.failIfNoSpecifiedTests=false` | 7 tests: valid remote wins; remote gets identical validation (tool/model); UNAUTHORIZED/INVALID/UNAVAILABLE/NOT_FOUND classified correctly; priority configurable |
| 22 Last-known-good | DONE | `src/main/java/nl/metafactory/agents/definition/cache/LastKnownGoodStore.java`, `AgentDefinitionDocument.rawMarkdown`, `cache.directory` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LastKnownGoodStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 tests: provenance/hash/expiry stored; remote only; expired→NOT_FOUND+deleted; tampered/corrupt→UNAVAILABLE+deleted; invalidation and capacity |
| 23 Fallback policy | DONE | `src/main/java/nl/metafactory/agents/definition/resolver/ConfiguredFallbackPolicy.java`, LKG hook in the resolver | `mvn -o -pl services/embabel-agent-service -am test -Dtest=FallbackPolicyMatrixTest -Dsurefire.failIfNoSpecifiedTests=false` | 13 table-driven tests: UNAVAILABLE/NOT_FOUND only with per-agent + config permission; UNAUTHORIZED/INVALID never; remote outage→LKG→local; stale cache; the cache does not bypass revoked tool rights or a mandatory guardrail |
| 24 LiteLLM policy binding | DONE (live: NOT_RUN_EXTERNAL) | `src/main/java/nl/metafactory/agents/definition/litellm/LiteLlmPolicyGuardrailExecutor.java`, `GuardrailExecutor.operationalFor`, `LiteLlmHttpClient.post`, `src/main/java/nl/metafactory/agents/config/AgentGatewayModelRegistrar.java`, `LiteLlmGatewayModelConfig.gatewayLlm`, `src/main/java/nl/metafactory/agents/definition/config/AgentDefinitionConfiguration.java`, `infrastructure/litellm/config.yaml` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LiteLlmPolicyGuardrailExecutorTest,AgentDefinitionConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 5+4 tests: gateway guardrail only bound when `POST /policies/resolve` (verified in v1.101.2) confirms the guardrail for the model alias; 401/500/timeout/missing attachment → GUARDRAIL_MISSING; never assumed without an alias. LiteLLM config: alias `customer-chat`, `litellm_content_filter` `openjcockpit-pii`, policy on `models: [customer-chat]`. Not tested live |
| 25 A2A server | DONE | `src/main/java/nl/metafactory/agents/a2a/A2AInboundAdapter.java`, `A2AProtocol.java` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=A2AServerContractTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 boot tests (real security + resolver + H2): 401 without JWT; `message/send` → A2A 0.3 `Message`; tenant claim missing/other tenant → TENANT_FORBIDDEN; stream −32004, tasks −32001, unknown −32601, parse/version/file-part/role errors. Own adapter instead of `embabel-agent-a2a` (which publishes all Embabel goals, has a hard-coded card, Jackson 2) |
| 26 Dynamic Agent Card | DONE | `src/main/java/nl/metafactory/agents/a2a/AgentCardController.java`, `PreparedAgent.description` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentCardContractTest -Dsurefire.failIfNoSpecifiedTests=false` | 3 tests: card deserialises to SDK `io.a2a.spec.AgentCard` 0.3.2.Final; real invocation URL; no streaming/push; only the agent itself as a skill (no internal skills/tools/guardrails); other tenant/unknown → 404 |
| 27 LiteLLM A2A link | DONE (roundtrip: NOT_RUN_EXTERNAL) | `infrastructure/litellm/a2a/customer-support.agent.yaml` + `.json`, `scripts/a2a-roundtrip.sh` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=LiteLlmRegistrationConfigTest -Dsurefire.failIfNoSpecifiedTests=false` | 3 tests: registration pinned to `protocolVersion "0.3.0"`/JSONRPC, URL/capabilities/guardrail consistent with the server and the LiteLLM config. No live registration performed; roundtrip not performed (LiteLLM/Docker/Keycloak not running) |
| 28 Security and regression tests | DONE | `src/test/java/nl/metafactory/agents/definition/security/AgentDefinitionSecurityRegressionTest.java` | `mvn -o -pl services/embabel-agent-service -am test` | 7 additional tests (prompt injection cannot grant tools, skill `allowed-tools` grants nothing, non-allowlisted tool, unauthorised/invalid skillId, tenant isolation, hallucinated tool name → Throw, PII in both directions) + earlier tests for path traversal, fail-closed, remote outage, cache expiry, version consistency. **Full suite: 1106 tests, 0 failures, 0 errors** (baseline 948) |
| 29 Operational documentation | DONE (demo: NOT_RUN_EXTERNAL) | `docs/agent-integration/OPERATIONS.md`, `demo-seed.sql`, `agent-definitions/**`, `src/main/java/nl/metafactory/agents/definition/config/AgentDefinitionsHealthIndicator.java`, `src/main/java/nl/metafactory/agents/definition/demo/DemoAgentTools.java`, `application.yml` | `mvn -o -pl services/embabel-agent-service -am test -Dtest=AgentDefinitionsOperationsTest -Dsurefire.failIfNoSpecifiedTests=false` | 4 tests: health indicator, demo tool only in profile `agent-demo`, bundled sample definitions valid. Demo steps documented, not performed in this environment |

## Step 01 — architecture note (confirmed from code)

**Build/versions**
- Maven multi-module; parent `spring-boot-starter-parent` 4.1.1, Java 25.
- Spring AI 2.0.1 (OpenAI + Ollama starters), MCP SDK 2.0.1, Jackson 3 (`tools.jackson`), YAML via `jackson-dataformat-yaml`.
- Embabel `embabel-agent-starter` 1.5.1.
- LiteLLM proxy `ghcr.io/berriai/litellm:v1.101.2` (`docker-compose.yml`, `infrastructure/litellm/config.yaml`).
- Package root: `nl.metafactory.agents`.

**Existing components**
- Agents: Embabel `@Agent` classes in `agent/` and `subagent/`; orchestration via `orchestration/EmbabelOrchestrator`.
- Model path: `config/LiteLlmGatewayModelConfig` routes the coding LLM via LiteLLM (`/v1/chat/completions`, `openjcockpit.llm.gateway.*`, key via `LITELLM_VIRTUAL_KEY`).
- Agent/skill/subagent definitions: **YAML files on disk** (`workflow/YamlDefinitionStore`, `AgentSpecRepository` → `<WORKFLOW_DEFINITIONS_PATH>/agents`). `AgentSpec` contains `name, instructions, skillNames, mcpTools, workflowId`; `SkillSpec` contains textual instructions + `mcpTools` + `policyNotes`.
- Policy: OPA (`policy/`), `PolicyGuardedMcpToolGateway` with FAIL_CLOSED/FAIL_OPEN mode — usable as a local tool authorisation executor.
- Security: OAuth2 resource server (Keycloak JWT), `SecurityConfig`, `JwtCurrentUserProvider`.
- PostgreSQL + Flyway: only `agent_runs`, `agent_run_events`, `agent_run_artifacts` (V1, V2). `agent_runs.customer_id` is the only tenant-like field.
- Tests: JUnit 5 / Spring Boot test, H2 for JPA, OkHttp `mockwebserver` for HTTP contract tests.

**Known gaps relative to the assignment**
1. **No agent structure in PostgreSQL and no tenant model.** Agent definitions are YAML on disk; there is no table for agent/tenant binding (steps 13–15 assume an existing schema).
2. **No native Embabel Skills API in 1.5.1.** The jars `embabel-agent-api/ai/autoconfigure/starter 1.5.1` contain no `Skill` classes, but do contain `PromptContributor` and `LlmReference`/`LlmReferenceProvider`. Step 11 has to fall back on those (or on an Embabel upgrade).
3. **A2A module:** locally only `embabel-agent-a2a` 0.2.0 (incompatible with the 1.5.1 generation); the availability of a 1.5.x version must be verified online.
4. **Repo rules (`AGENTS.md`):** every feature must be recorded as `specs/NNN-slug/` (spec/plan/tasks/review), and changes to authentication/authorisation/logging require explicit human approval. This assignment adds an inbound A2A endpoint and tool authorisation.
5. `infrastructure/mock-skills-marketplace` (nginx + `skills.json`) exists; still to be determined whether it is a representative contract for step 20.

## Decisions
- **B1 (2026-10-14)** — The owner approves the auth/authz/logging changes; the feature is recorded as `specs/002-agent-definition-a2a/`.
- **B2 (2026-10-14)** — New minimal Flyway migration for agent/tenant binding; tenant = JWT claim / customerId.
- **B3 (2026-10-14)** — The owner approves the V3 migration for all databases (moved to `db/migration`) and asks for A2A to be enabled in docker-compose with a seeded LiteLLM key; locally: Keycloak service client `openjcockpit-a2a` (tenant `local`), LiteLLM caller key, `db/seed-local` bindings.

## Established Embabel 1.5.1 APIs (via javap/sources, not assumed)
- `PromptRunner.withPromptContributor(s)`, `withReference(LlmReference)`, `withTool(Tool)`, `withGuardRails(GuardRail...)`, `withToolCallInspectors(...)`, `withToolNotFoundPolicy(...)`.
- Guardrails: `UserInputGuardRail`, `AssistantMessageGuardRail`; only `ValidationSeverity.CRITICAL` throws `GuardRailViolationException`; deduplicated per class.
- Native Skills: separate module `com.embabel.agent:embabel-agent-skills:1.5.1` (Maven Central) with `Skills` (`LlmReference`), `withLocalSkill`, `asIndividualReferences()`, default `NoOpExecutionEngine` (scripts not executed). Reads lazily from disk → copy materialised per run.
- The default `ToolNotFoundPolicy` is `AutoCorrectionPolicy` (maps an unknown tool name to a similar tool) → replaced by a strict policy.

## Step 13 — PostgreSQL mapping investigation

Confirmed from `src/main/resources/db/migration/V1__create_agent_run_history.sql`, `V2__...`, the JPA entities in `persistence/` and `infrastructure/keycloak/openjcockpit-realm.json`:

| Concept from the assignment | Exists in repo? | Where / remark |
|---|---|---|
| Agent structure table | No | Agent/skill/subagent specs are YAML on disk (`YamlDefinitionStore`, `<WORKFLOW_DEFINITIONS_PATH>/agents|skills|subagents`). |
| Tenant table / tenant binding | No | Only `agent_runs.customer_id` (free text, per run). No FK, no tenant entity. |
| Tenant in JWT | No | The Keycloak realm has no tenant/customer claim mapper; `JwtCurrentUserProvider` only reads `preferred_username`/`sub`. |
| Repositories | Run history only | `AgentRunRecordRepository`, `AgentRunEventRecordRepository`, `AgentRunArtifactRecordRepository` (JPA, `ddl-auto: validate`). |
| Migrations | Flyway V1, V2 | `classpath:db/migration`; tests on H2 (`MODE=PostgreSQL`). |

**Consequence (decision B2):** there is no existing schema to reuse. Step 14 adds a minimal, versioned migration in a **separate Flyway location** `classpath:db/migration-agent-definitions` that is only active in tests. Production only activates it after approval, by adding that location to `spring.flyway.locations`. The tenant comes from a configurable JWT claim (`openjcockpit.agent-definitions.tenant-claim`, default `tenant_id`); a token without that claim is refused. A Keycloak claim mapper still has to be added (realm config not changed: authentication config, out of scope without separate approval).

## Final overview

### Architecture
See `OPERATIONS.md` §1. Core: `A2AInboundAdapter` → `AgentRunService` (one resolution, `RunContext`, MDC)
→ `AgentDefinitionResolver` (PostgreSQL binding + `SourceChainSelector`[REMOTE→LKG→LOCAL] + `SkillResolver`
+ `ToolAccessPolicy` + `GuardrailPolicyEnforcer` + `ModelAliasCatalog`) → immutable `PreparedAgent`
→ `ConfiguredDefinitionAgent` (Embabel `@Agent`/`@Action`; `withLlmByRole`, `AgentPromptContributor`,
native `Skills`, `SnapshotBoundTool`, Embabel guardrails, strict ToolNotFoundPolicy) → LiteLLM model gateway.

### Security measures
- JWT required on all A2A paths (existing resource server); tenant from the claim, otherwise refused; card 404 for unauthorised callers.
- Tenant binding in PostgreSQL; queries always on (tenant, agent); tool allowlist server-side; a definition/skill/prompt cannot add anything.
- Tools: bind-time allowlist + call-time snapshot check + OPA; unknown tool names abort (no auto-correction).
- Guardrails fail closed: a mandatory id without an operational executor blocks; Embabel guards at CRITICAL; gateway guard only after LiteLLM confirmation.
- Files: confined root, no symlinks/traversal, size limits, strict frontmatter, scripts never loaded/executed.
- Last-known-good: hash verification, expiry, only validated remote content; permissions always re-checked.
- Logging: only ids/statuses/hashes; no prompts, messages, tool data or keys. API key never in `toString`.

### Known limitations
See `OPERATIONS.md` §11 (remote content/skills UNSUPPORTED in LiteLLM v1.101.2; A2A 0.3 `message/send` only;
no Keycloak tenant mapper; V3 migration test-only; Embabel DEBUG logging; policy engine not verified live).

### Live tests
| Test | Status |
|---|---|
| Unit/contract/boot tests (1106) | run, green |
| Local Embabel run with a real LLM (step 12) | NOT_RUN_EXTERNAL |
| LiteLLM `/policies/resolve` against a real instance (step 24) | NOT_RUN_EXTERNAL |
| LiteLLM → Embabel → LiteLLM roundtrip (step 27, `scripts/a2a-roundtrip.sh`) | NOT_RUN_EXTERNAL |
| Local demo (step 29, `OPERATIONS.md` §9) | NOT_RUN_EXTERNAL |

### LiteLLM registration
`OPERATIONS.md` §7 — `infrastructure/litellm/a2a/customer-support.agent.yaml` (config merge) or `.json` (`POST /v1/agents`);
both only after approval against a shared instance.

### Consolidation (2026-10-14, B4)
- One reusable `A2AProviderClient` per A2A provider (`nl.metafactory.agents.a2a.client`), used by the catalogue, agent cards and the guardrail check; replaces the separate LiteLLM clients of the catalogue and agent definitions.
- One LiteLLM A2A key `openjcockpit-a2a` (replaces the catalogue key and caller key), one Keycloak A2A client.
- One compose seed service `local-seed` (replaces `litellm-seed-key` and `keycloak-a2a-client`) and one Spring profile `local` (replaces `a2a`).
- Tests: `LiteLlmA2AProviderClientTest`, `A2AProviderClientsTest`, `ComposeA2AWiringContractTest`, `A2ALocalStackProfileTest`.

### Workflows → catalogue agents (2026-10-14, B5)
- Owner: built-in stages fully replaced; sequential chain; local agents in-process; gates/orbs on agent references.
- `workflow_agent_refs` (V4), `AgentRef`, `WorkflowAgentLinks`, `CatalogAgentReferenceCheck`, `WorkflowAgentInvoker`,
  `ClientCredentialsTokens`, `EmbabelOrchestrator.pipeline` (then `catalogPipeline`); dashboard picker per catalogue section with execution order.
- Tests: `AgentRefTest`, `JdbcWorkflowAgentLinksTest`, `WorkflowAgentLinkingTest`, `CatalogAgentReferenceCheckTest`,
  `WorkflowAgentInvokerTest`, `ClientCredentialsTokensTest`, `CatalogPipelineTest`, `linkableAgents.test.tsx`.

### Built-in pipeline removed (2026-10-15, B7)
- Owner: "the workflow is the only execution engine which executes".
- Removed: `agent/*`, `subagent/*`, `domain/*` (except `SpecContent`), `CodeRealisationService`, `GitToolClient`,
  `SpecGitPublisher` and related classes, `GitCommitOutcomeClassifier`, `AgentDefinition`, `AgentPipelineProperties`; endpoints
  `GET /api/agents` and `POST /api/agent-runs` (embabel, ai-control `AgentController`, contract, dashboard `api.ts`).
- `EmbabelOrchestrator.start` refuses anything that is not an agent reference; `AgentOrchestrator` = `start`/`get`/`stop`.
- Tests ported to references: `EmbabelOrchestratorPauseResumeTest` (real gate/decision wiring),
  `…DefinitionFailureEventTest`, `…GateOrbMutualExclusionTest`, `…RunStateLostTest`, `CatalogPipelineTest`;
  e2e `agent-llm-gateway.spec.ts` now starts workflow `wf-spec-create`.

### git-mcp-server via LiteLLM (2026-10-15, B8)
- `mcp_servers.openjcockpit_git` in `infrastructure/litellm/config.yaml` (verified against the v1.101.2 source: `MCPServerConfig`,
  `transport: sse`, name without `-`, fixed `server_id`, `allow_all_keys`, `available_on_public_internet`).
- `local-seed`: key `embabel-agent-service` gets `object_permission.mcp_servers`; compose: `litellm` depends on `git-mcp-server`.
- Test: `ComposeA2AWiringContractTest.gitMcpServerIsRegisteredWithLiteLlmAndGrantedToTheAgentServiceKey`. Not yet verified live.

### Git-workspace jobs via the git-mcp-server (2026-10-15, B9)
- Owner: jobs run the workflow where the repo was cloned; git-mcp via LiteLLM for clone/branch/commit.
  Chosen: workspace in the git-mcp-server container; credentials pulled from ai-control (approved, auth change);
  job commit + push; ai-control-service identity for job → embabel (approved, auth change).
- git-mcp-server: `GitCredentialResolver`/`AiControlGitCredentialResolver`, tools without `username`/`token`.
- service-common: `ClientCredentialsTokens`, `LiteLlmMcpToolClient` (`/mcp-rest/tools/call`), `GitMcpGateway`.
- ai-control: `/internal/git-workspaces/{key}/credentials`, `GitWorkspaceProperties`/`GitWorkspaceConfig`,
  job flow via `GitMcpGateway`, `EmbabelAgentClient.*As(authorization, …)`; bug fix: the final status survives cleanup.
- embabel: `gitworkspace/*` (binding, registry, properties, tools, `CallerClient`), `RunScopedTool`,
  `RunContext.gitWorkspace`, A2A header `x-openjcockpit-workflow-run`; seeded agents v1.1.0 with git tools.
- Seed: Keycloak clients `openjcockpit-git-mcp` and `openjcockpit-ai-control` (`keycloak-service-clients.sh`),
  LiteLLM key `ai-control-service`; git-mcp-server no longer published on the host.
- Tests: among others `AiControlGitCredentialResolverTest`, `GitWorkspaceCredentialServiceTest`,
  `GitWorkspaceCredentialsControllerTest`, `GitWorkspaceToolsTest`, `A2AInboundGitWorkspaceTest`,
  `WorkflowExecutionServiceGitWorkspaceTest`, `GitMcpGatewayTest`. Not yet verified live.

### Shared workspace for dashboard runs + checkpoint commits (2026-10-16, B10)
- Reason: on dashboard starts no agent got a workspace ("No git workspace is bound").
- Owner chose: one shared workspace per run, a branch per workflow+run, checkpoint commits after every agent, and a
  **single-use ticket** as the binding (approved, auth change): the run starts with the user's token.
- embabel: `GitWorkspaceTickets` + `POST /internal/git-workspace-tickets` (only `azp=openjcockpit-ai-control`),
  `WorkflowStartInput.gitWorkspaceTicket` replaces `gitWorkspaceKey`, `GitWorkspaceBinding.branch`,
  `RunCheckpoints`/`GitWorkspaceCheckpoints` in `EmbabelOrchestrator`; seeded agents v1.1.1 (prompt text).
- ai-control: `ProjectWorkflowRunService` (dashboard start on a project), `GitWorkspaceJobService.prepareWorkflowRunWorkspace`
  / `finishWorkflowRunAsync`, `BranchNameGenerator.forWorkflowRun`, jobs bind via a ticket; embabel now writes agent
  outputs per checkpoint; `BearerTokenRelayFilter` leaves an explicit `Authorization` in place.
- Tests: `GitWorkspaceTicketsTest`, `GitWorkspaceTicketControllerTest`, `GitWorkspaceCheckpointsTest`,
  `CatalogPipelineTest.aCheckpointFollowsEveryAgentBeforeTheNextOneStarts`, `ProjectWorkflowRunServiceTest`,
  `BearerTokenRelayFilterTest`, extended `WorkflowExecutionServiceGitWorkspaceTest`/`GitWorkspaceJobServiceTest`.

### Managing A2A services in the dashboard (2026-10-16, B11)
- Workflow designer → Configuration got an **A2A services** block: services from `openjcockpit.a2a-providers`
  (read-only, e.g. LiteLLM) plus LiteLLM-compatible gateways you add, change or delete there.
- Owner chose (approved, auth change): storage in the embabel database with encrypted secrets, only
  LiteLLM-compatible gateways, API key **plus** an optional caller identity (client credentials).
- embabel: `V6__create_a2a_services.sql`, `a2a.services.*` (`A2AServices`, `A2AServiceStore`, `A2AServiceSecrets`,
  `A2AServiceController` on `/api/a2a-services`); `A2AProviderClients.replaceAdded` (configuration providers fixed,
  dashboard providers replaceable; `litellm()` remains configuration only); `Provider.id`; caller tokens now belong to
  the client (`A2AProviderClient.callerAuthorization`), no longer to `WorkflowAgentInvoker`.
- ai-control: contract `paths/a2a-services.yaml` + `schemas/a2a-service.yaml`, `A2AServiceController` (relay).
- dashboard: `A2AServicesSettings` (table + add/edit form), `listA2AServices`/`create`/`update`/`delete`.
- Tests: `A2AServicesTest` (H2), `A2AServiceSecretsTest`, `A2AServiceControllerTest` (both services),
  `EmbabelAgentClientA2AServicesTest`, extended `A2AProviderClientsTest`/`WorkflowAgentInvokerTest`,
  `A2AServicesSettings.test.tsx`.

### Open decisions for the owner
1. ~~Activate the V3 migration~~ — approved and active (B3).
2. Keycloak claim mapper for `tenant_id` for real tenants (solved locally with a service client, B3).
3. `logging.level.com.embabel.agent` to `INFO` in production.
4. Register the agent in LiteLLM and run the live roundtrip.
5. ~~Git-workspace jobs to catalogue agents~~ — done (B6, 2026-10-15): a job runs a workflow's agents and commits their outputs to the job branch.
6. ~~Remove the built-in pipeline~~ — done (B7, 2026-10-15): the workflow is the only execution engine.
7. ~~Remove the MCP tool layer/git-mcp-server/git credentials?~~ — no (2026-10-15): the git-mcp-server stays as a separate MCP server via LiteLLM (B8), git credentials stay.
8. ~~Run the workflow where the repository was cloned~~ — done (B9): job workspace in the git-mcp-server via LiteLLM.
9. ~~Token as a tool argument~~ — solved (B9): credentials server-side (git-mcp-server ← ai-control).
10. Orb child workflows do not inherit the parent's git workspace; `startedBy` of a job run (API job, not
    dashboard) is ai-control's service account. Workspace bindings and tickets are in memory (lost on
    restart). The job stops a run after `WORKFLOW_AGENT_TIMEOUT`, even when it is waiting at an approval gate.
11. Who may change A2A services: currently every logged-in user (like the OPA config); there is no admin role yet
    (`@PreAuthorize` is missing in both services). The supplied URLs are only checked for form (http(s),
    host, no credentials/query) — an internal host is actually needed locally, so there is no SSRF blocklist.
