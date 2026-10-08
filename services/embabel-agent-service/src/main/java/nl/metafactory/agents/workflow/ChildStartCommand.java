package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;

import java.util.List;

/**
 * Carries everything needed to start a child workflow from a workflow orb — the target
 * workflow id, the parent's already-enriched AgentRunRequest (used to re-project context onto the
 * child per ADR-004: prompt/repositoryUrl/gitUsername/gitToken/baseBranch), the orb's execution
 * mode, the RunInitiator to carry through unchanged (never re-derived), and the current chain
 * ancestry (workflow ids from the top-level run down to and including the parent's own workflow).
 */
public record ChildStartCommand(
        String targetWorkflowId,
        AgentRunRequest parentRequest,
        WorkflowOrbMode mode,
        RunInitiator initiator,
        List<String> chainAncestry
) {
}
