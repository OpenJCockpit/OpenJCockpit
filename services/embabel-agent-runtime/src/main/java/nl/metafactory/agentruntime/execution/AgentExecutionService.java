package nl.metafactory.agentruntime.execution;

import com.embabel.agent.api.validation.guardrails.GuardRailViolationException;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.definition.runtime.AgentRunService;
import nl.metafactory.agentruntime.definition.runtime.CatalogAgentRuns;
import nl.metafactory.agentruntime.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.agentruntime.workflowrun.WorkflowRunScope;
import nl.metafactory.agentruntime.workflowrun.WorkflowRunScopeResolver;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Runs one agent for the workflow engine. Each execution id runs at most once ({@link ExecutionRegistry});
 * the workflow run scope is resolved from the engine, never taken from the request; catalog agents run
 * from the snapshot in the request, every other agent from the runtime's own definition sources. Message
 * text and output are never logged.
 */
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    /** Upper bound of one message; the engine's own budget (agent-workflows.max-message-chars) is far lower. */
    static final int MAX_MESSAGE_CHARS = 512 * 1024;

    private final AgentRunService served;
    private final CatalogAgentRuns catalog;
    private final WorkflowRunScopeResolver scopes;
    private final ExecutionRegistry registry;

    public AgentExecutionService(AgentRunService served, CatalogAgentRuns catalog, WorkflowRunScopeResolver scopes,
                                 ExecutionRegistry registry) {
        this.served = served;
        this.catalog = catalog;
        this.scopes = scopes;
        this.registry = registry;
    }

    public AgentExecutionResult execute(AgentExecutionRequest request) {
        validate(request);
        if (!registry.begin(request.executionId())) {
            log.warn("agent.execution.duplicate executionId={}", request.executionId());
            throw new AgentExecutionException(HttpStatus.CONFLICT, AgentExecutionFailure.DUPLICATE_EXECUTION,
                    "Execution " + request.executionId() + " was already accepted; it is not run again");
        }
        try {
            var response = run(request, scopeOf(request));
            registry.complete(request.executionId());
            return new AgentExecutionResult(request.executionId(), response.agentId(), response.version(),
                    response.contentHash(), response.traceId(), response.text());
        } catch (AgentExecutionException e) {
            registry.fail(request.executionId(), e.reason());
            throw e;
        }
    }

    private ConfiguredDefinitionAgent.Response run(AgentExecutionRequest request, WorkflowRunScope scope) {
        var runs = request.definition() == null ? served : catalog.forSnapshot(request.definition());
        try {
            return runs.run(request.tenantId(), request.agentId(), request.principal(), request.traceparent(),
                    request.message(), scope);
        } catch (AgentResolutionException e) {
            boolean transientFailure = e.failure() == ResolutionFailure.UNAVAILABLE;
            throw new AgentExecutionException(transientFailure ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.UNPROCESSABLE_CONTENT, e.failure().name(),
                    "Agent " + request.agentId() + " not runnable: " + e.failure());
        } catch (GuardRailViolationException e) {
            log.warn("agent.execution.blocked executionId={} agentId={} guard={}", request.executionId(),
                    request.agentId(), e.getGuard());
            throw new AgentExecutionException(HttpStatus.UNPROCESSABLE_CONTENT,
                    AgentExecutionFailure.GUARDRAIL_VIOLATION, "Request blocked by guardrail");
        } catch (RuntimeException e) {
            log.error("agent.execution.failed executionId={} agentId={} error={}", request.executionId(),
                    request.agentId(), e.getClass().getSimpleName());
            throw new AgentExecutionException(HttpStatus.INTERNAL_SERVER_ERROR,
                    AgentExecutionFailure.EXECUTION_FAILED, "Agent " + request.agentId() + " failed: "
                    + e.getClass().getSimpleName());
        }
    }

    /** The run's scope as the engine reports it; a named run that cannot be resolved fails the execution. */
    private WorkflowRunScope scopeOf(AgentExecutionRequest request) {
        if (request.workflowRunId() == null) {
            return null;
        }
        try {
            return scopes.find(request.workflowRunId()).orElseThrow(() -> new AgentExecutionException(
                    HttpStatus.UNPROCESSABLE_CONTENT, AgentExecutionFailure.INVALID_REQUEST,
                    "Workflow run " + request.workflowRunId() + " is not active"));
        } catch (AgentServiceClient.ManagementUnavailableException e) {
            throw new AgentExecutionException(HttpStatus.SERVICE_UNAVAILABLE, ResolutionFailure.UNAVAILABLE.name(),
                    "Workflow run scope is unavailable");
        }
    }

    private static void validate(AgentExecutionRequest request) {
        String problem = null;
        if (request.executionId() == null || !AgentExecutionRequest.EXECUTION_ID.matcher(request.executionId()).matches()) {
            problem = "executionId is missing or contains unsafe characters";
        } else if (blank(request.tenantId()) || blank(request.agentId()) || blank(request.principal())) {
            problem = "tenantId, agentId and principal are required";
        } else if (request.workflowRunId() != null && !AgentExecutionRequest.EXECUTION_ID.matcher(request.workflowRunId()).matches()) {
            problem = "workflowRunId contains unsafe characters";
        } else if (request.message() == null || request.message().length() > MAX_MESSAGE_CHARS) {
            problem = "message is missing or too long";
        }
        if (problem != null) {
            throw new AgentExecutionException(HttpStatus.BAD_REQUEST, AgentExecutionFailure.INVALID_REQUEST, problem);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
