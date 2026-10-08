package nl.metafactory.aicontrol.client;

import java.time.Instant;
import java.util.List;

public record WorkflowDefinitionDto(
        String id,
        String name,
        String projectName,
        String groupId,
        String description,
        List<String> agentIds,
        List<String> subagentNames,
        List<String> skillNames,
        List<McpToolRefDto> mcpTools,
        TriggerConfigDto trigger,
        ExecutionConfigDto execution,
        boolean promptRequired,
        String promptInstructions,
        String status,
        String lastExecutionStatus,
        Instant lastExecutionAt,
        // F1 (workflow-approval-gate architecture §3.1): this hand-written record is what
        // WorkflowController/EmbabelAgentClient relay verbatim between the dashboard and
        // embabel-agent-service. Omitting this component would silently discard every gate a
        // user configures in the dashboard during that relay — 200 response, no error, no log.
        ApprovalGateConfigDto approvalGate,
        // workflow-trigger-workflow-orb architecture §5.1: same relay posture as approvalGate
        // above — this hand-written record is what WorkflowController/EmbabelAgentClient relay
        // verbatim between the dashboard and embabel-agent-service. Omitting this component
        // would silently discard every workflow orb a user configures during that relay.
        List<WorkflowOrbDto> workflowOrbs
) {
}
