package nl.metafactory.agents.persistence;

import java.time.Instant;

/**
 * JPQL constructor-expression projection of the single most recent run of one workflow id,
 * mirroring the existing {@link AgentRunSummaryProjection} shape in this same package.
 */
public class WorkflowLastExecutionProjection {

    private final String workflowId;
    private final String status;
    private final Instant startedAt;

    public WorkflowLastExecutionProjection(String workflowId, String status, Instant startedAt) {
        this.workflowId = workflowId;
        this.status = status;
        this.startedAt = startedAt;
    }

    public String getWorkflowId() { return workflowId; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
}
