package nl.metafactory.agentruntime.definition.runtime;

import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Per-call tool authorisation by the agent service, which owns the policy decision point: it evaluates the
 * existing OPA guard ({@code mcp.tool.invoke}) with its own, runtime-editable OPA configuration and writes
 * the decision to its audit log. Only identifiers are sent. Any failure to obtain a decision denies the
 * call (fail closed); bind-time allowlisting in {@code ToolAccessPolicy} remains the primary control.
 */
@Component
public class AgentServiceToolCallAuthorizer implements ToolCallAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceToolCallAuthorizer.class);

    private final AgentServiceClient agentService;

    public AgentServiceToolCallAuthorizer(AgentServiceClient agentService) {
        this.agentService = agentService;
    }

    @Override
    public Decision authorize(RunContext run, String toolId) {
        // An agent run inside a workflow run is attributed to that workflow and run; an agent run outside any
        // workflow (e.g. a direct A2A call) has no workflow, which the policy refuses (fail closed).
        var workflow = run.workflow();
        var request = new ToolInvocationDecision.Request(workflow == null ? null : workflow.workflowId(),
                workflow == null ? run.runId() : workflow.workflowRunId(), run.tenantId(), run.agentId(), toolId,
                run.principal(),
                Map.of("traceId", run.traceId(), "agentVersion", run.version(), "agentRunId", run.runId()));
        try {
            var decision = agentService.decideToolInvocation(request);
            return decision.allowed() ? Decision.allow() : Decision.deny(decision.reason());
        } catch (RuntimeException e) {
            log.warn("agent.tool.decision.unavailable tool={} {}", toolId, run.logFields());
            return Decision.deny("policy decision unavailable");
        }
    }
}
