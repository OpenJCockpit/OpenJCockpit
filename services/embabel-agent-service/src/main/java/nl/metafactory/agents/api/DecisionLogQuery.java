package nl.metafactory.agents.api;

import nl.metafactory.agents.policy.model.DecisionLogFilter;

import java.time.Instant;

/** Query-string binding for the {@code decision-logs} endpoints; {@code result} maps to the filter's decision result. */
public record DecisionLogQuery(
        Instant from,
        Instant to,
        String agentId,
        String subagentId,
        String skillId,
        String mcpToolName,
        String result
) {

    DecisionLogFilter toFilter() {
        return new DecisionLogFilter(from, to, agentId, subagentId, skillId, mcpToolName, result);
    }
}
