package nl.metafactory.agents.policy.model;

import java.time.Instant;

/**
 * Optional filters for decision log export. All fields are nullable — a null field means
 * "don't filter on this."
 */
public record DecisionLogFilter(
        Instant from,
        Instant to,
        String agentId,
        String subagentId,
        String skillId,
        String mcpToolName,
        String decisionResult
) {

    public static DecisionLogFilter none() {
        return new DecisionLogFilter(null, null, null, null, null, null, null);
    }
}
