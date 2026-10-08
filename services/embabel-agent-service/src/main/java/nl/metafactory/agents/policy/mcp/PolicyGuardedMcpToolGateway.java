package nl.metafactory.agents.policy.mcp;

import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * The only entry point for MCP tool invocation: Agent/Subagent -> Skill -> this gateway -> tool.
 * Always calls PolicyDecisionService.canInvokeMcpTool(...) first and only delegates to
 * McpToolExecutor when allowed — there is no other path for a Workflow Editor-defined agent to
 * reach an MCP tool, so this holds regardless of whether OPA itself is enabled/reachable.
 */
@Service
public class PolicyGuardedMcpToolGateway {

    private static final Logger log = LoggerFactory.getLogger(PolicyGuardedMcpToolGateway.class);

    private final PolicyDecisionService policyDecisionService;
    private final McpToolExecutor executor;

    public PolicyGuardedMcpToolGateway(PolicyDecisionService policyDecisionService, McpToolExecutor executor) {
        this.policyDecisionService = policyDecisionService;
        this.executor = executor;
    }

    public McpToolResult invoke(McpToolInvocation invocation) {
        var context = new PolicyDecisionContext(
                invocation.workflowId(), invocation.workflowExecutionId(), invocation.projectId(),
                invocation.projectName(), invocation.customerId(), invocation.customerName(), null, null,
                invocation.agentId(), null, invocation.subagentId(), null, invocation.skillId(), null,
                invocation.toolName(), invocation.operation(), "mcp.tool.invoke", null, null, null, null,
                null, null, null, Instant.now(), Map.of());

        var decision = policyDecisionService.canInvokeMcpTool(context);
        if (!decision.allowed()) {
            log.info("MCP tool invocation blocked by policy: tool={} workflow={} reason={}",
                    invocation.toolName(), invocation.workflowId(), decision.reason());
            return McpToolResult.blocked(decision.reason() != null ? decision.reason() : "Blocked by policy");
        }
        return executor.execute(invocation);
    }
}
