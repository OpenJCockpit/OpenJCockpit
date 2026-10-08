package nl.metafactory.agents.policy.model;

/**
 * Wraps a PolicyDecisionContext in OPA's expected {"input": {...}} envelope.
 */
public record OpaDecisionRequest(PolicyDecisionContext input) {
}
