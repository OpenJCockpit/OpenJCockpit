package nl.metafactory.agents.policy.model;

/**
 * Behavior when OPA is enabled/configured but not reachable.
 * FAIL_CLOSED: sensitive actions are blocked. FAIL_OPEN: actions continue but are flagged
 * as policy-check-unavailable in the audit trail.
 */
public enum FailMode {
    FAIL_CLOSED,
    FAIL_OPEN
}
