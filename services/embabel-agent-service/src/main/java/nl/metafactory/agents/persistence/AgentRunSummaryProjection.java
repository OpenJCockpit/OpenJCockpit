package nl.metafactory.agents.persistence;

import java.time.Instant;

public class AgentRunSummaryProjection {

    private final String runId;
    private final String workflowId;
    private final String status;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String startedBy;

    public AgentRunSummaryProjection(String runId, String workflowId, String status,
                                      Instant startedAt, Instant completedAt, String startedBy) {
        this.runId = runId;
        this.workflowId = workflowId;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.startedBy = startedBy;
    }

    public String getRunId() { return runId; }
    public String getWorkflowId() { return workflowId; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getStartedBy() { return startedBy; }
}
