package nl.metafactory.agents.definition.runtime;

import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Per-call tool authorisation through the existing OPA guard ({@code mcp.tool.invoke}), carrying
 * tenant, agent and trace identifiers only. When OPA is disabled the existing service allows the
 * call; bind-time allowlisting in {@code ToolAccessPolicy} remains the primary control. Whether OPA
 * counts as an enforced guardrail is decided separately by its guardrail executor.
 */
@Component
public class OpaToolCallAuthorizer implements ToolCallAuthorizer {

    private final PolicyDecisionService policyDecisionService;

    public OpaToolCallAuthorizer(PolicyDecisionService policyDecisionService) {
        this.policyDecisionService = policyDecisionService;
    }

    @Override
    public Decision authorize(RunContext run, String toolId) {
        // An agent run inside a workflow run is attributed to that workflow and run; an agent run outside any
        // workflow (e.g. a direct A2A call) has no workflow, which the policy refuses (fail closed).
        var workflow = run.workflow();
        var context = new PolicyDecisionContext(workflow == null ? null : workflow.workflowId(),
                workflow == null ? run.runId() : workflow.workflowRunId(),
                null, null, run.tenantId(), null, null,
                "a2a", run.agentId(), null, null, null, null, null, toolId, "invoke", "mcp.tool.invoke",
                run.principal(), null, null, null, null, null, null, Instant.now(),
                Map.of("traceId", run.traceId(), "agentVersion", run.version(), "agentRunId", run.runId()));
        var decision = policyDecisionService.canInvokeMcpTool(context);
        return decision.allowed() ? Decision.allow() : Decision.deny(decision.reason());
    }
}
