package nl.metafactory.agents.approval.model;

/**
 * The four data-level outcomes of a gated stage's block (BR-40). Never appears in a control-flow
 * condition outside {@link nl.metafactory.agents.approval.StageChangeReports} (the only producer)
 * and presentation — see ADR-003. There is exactly one gate-open path; this enum is the data that
 * distinguishes the four cases, not four separate code paths.
 */
public enum StageOutcome {
    /** Stage completed and published cleanly. */
    PUBLISHED,
    /** Stage produced changes but branch/commit/push/PR failed. */
    PUBLISH_FAILED,
    /** Agent returned no implementable change, or the commit had nothing new to record. */
    NO_CHANGE,
    /** The gated stage performs no git publication at all. */
    NOT_APPLICABLE
}
