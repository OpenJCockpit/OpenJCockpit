package nl.metafactory.agents.policy.model;

import java.time.Instant;
import java.util.List;

public record PolicyDecision(
        boolean allowed,
        String reason,
        boolean requiredApproval,
        String riskLevel,
        List<String> auditTags,
        String opaDecisionId,
        String policyPath,
        String policyRevision,
        Instant decisionTimestamp,
        boolean policyUnavailable,
        String failModeApplied
) {

    public static PolicyDecision opaDisabled(String reason, Instant timestamp) {
        return new PolicyDecision(true, reason, false, "none", List.of("opa-disabled"),
                null, null, null, timestamp, false, null);
    }

    public static PolicyDecision unavailable(FailMode failMode, String reason, Instant timestamp) {
        boolean allowed = failMode == FailMode.FAIL_OPEN;
        return new PolicyDecision(allowed, reason, false, "unknown", List.of("policy-unavailable"),
                null, null, null, timestamp, true, failMode.name());
    }
}
