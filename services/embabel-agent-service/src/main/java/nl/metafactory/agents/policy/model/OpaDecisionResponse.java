package nl.metafactory.agents.policy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors OPA's standard decision response shape:
 * {"result": {"allowed": ..., "reason": ...}, "decision_id": "..."}
 */
public record OpaDecisionResponse(
        OpaResult result,
        @JsonProperty("decision_id") String decisionId
) {

    public record OpaResult(
            boolean allowed,
            String reason,
            boolean requiredApproval,
            String riskLevel,
            List<String> auditTags
    ) {
    }
}
