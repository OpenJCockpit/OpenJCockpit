package nl.metafactory.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import nl.metafactory.agents.orchestration.RunFailureDiagnostics;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRunRecord {

    @Id
    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "workflow_id", length = 128)
    private String workflowId;

    @Column(name = "customer_id", length = 255)
    private String customerId;

    @Column(name = "spec_file", columnDefinition = "TEXT")
    private String specFile;

    @Column(name = "repository_url", length = 1000)
    private String repositoryUrl;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "started_by", length = 255)
    private String startedBy;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "failure_summary", length = RunFailureDiagnostics.MAX_LENGTH)
    private String failureSummary;

    public AgentRunRecord() {}

    public AgentRunRecord(String runId, String workflowId, String customerId, String specFile,
                           String repositoryUrl, String status, Instant startedAt, Instant completedAt,
                           String startedBy, Instant reconciledAt, String failureSummary) {
        this.runId = runId;
        this.workflowId = workflowId;
        this.customerId = customerId;
        this.specFile = specFile;
        this.repositoryUrl = repositoryUrl;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.startedBy = startedBy;
        this.reconciledAt = reconciledAt;
        this.failureSummary = failureSummary;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSpecFile() { return specFile; }
    public void setSpecFile(String specFile) { this.specFile = specFile; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public Instant getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Instant reconciledAt) { this.reconciledAt = reconciledAt; }
    public String getFailureSummary() { return failureSummary; }
    public void setFailureSummary(String failureSummary) { this.failureSummary = failureSummary; }
}
