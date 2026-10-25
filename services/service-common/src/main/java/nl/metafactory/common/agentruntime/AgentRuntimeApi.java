package nl.metafactory.common.agentruntime;

/**
 * The HTTP contract between {@code embabel-agent-service} (management plane: workflows, catalog, tenant
 * bindings, policy, run state) and {@code embabel-agent-runtime} (execution plane: Embabel, LLMs, tools,
 * inbound A2A). Both directions are internal, service-to-service, authenticated with Keycloak client
 * credentials; the receiving side authorises the caller by its {@code azp} claim and fails closed.
 *
 * <p>Neither side shares a Spring context, database or filesystem with the other: everything one side
 * needs from the other travels through these endpoints.
 */
public final class AgentRuntimeApi {

    /** Runtime: runs one agent synchronously ({@link AgentExecutionRequest} → {@link AgentExecutionResult}). */
    public static final String EXECUTIONS = "/internal/v1/agent-executions";

    /** Runtime: state of one execution by id ({@link AgentExecutionStatus}); never contains output. */
    public static final String EXECUTION = EXECUTIONS + "/{executionId}";

    /** Runtime: LLM-generated definition drafts ({@link DefinitionDrafts}); {@code kind} is agent, subagent or skill. */
    public static final String DEFINITION_DRAFT = "/internal/v1/definition-drafts/{kind}";

    /** Agent service: base path of the management data the runtime reads. */
    public static final String MANAGEMENT = "/internal/agent-runtime/v1";

    /** Agent service: the tenant's binding of one agent ({@link AgentTenantBinding}); 404 when absent. */
    public static final String TENANT_BINDING = MANAGEMENT + "/tenants/{tenantId}/agent-bindings/{agentId}";

    /** Agent service: an active workflow run of this service ({@link WorkflowRunDescriptor}); 404 when unknown. */
    public static final String WORKFLOW_RUN = MANAGEMENT + "/workflow-runs/{runId}";

    /** Agent service: policy decision (OPA, audited) for one tool call ({@link ToolInvocationDecision}). */
    public static final String TOOL_INVOCATION_DECISIONS = MANAGEMENT + "/tool-invocation-decisions";

    /**
     * Header carrying a workflow run id to an agent (A2A: forwarded by LiteLLM as
     * {@code x-a2a-<agent>-x-openjcockpit-workflow-run}). Only a run id travels; the runtime asks the agent
     * service for the run's scope, so no caller can name a workflow or git workspace itself.
     */
    public static final String WORKFLOW_RUN_HEADER = "x-openjcockpit-workflow-run";

    private AgentRuntimeApi() {
    }
}
