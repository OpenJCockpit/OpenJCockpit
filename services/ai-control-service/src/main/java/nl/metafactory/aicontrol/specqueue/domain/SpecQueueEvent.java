package nl.metafactory.aicontrol.specqueue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit record; every column is non-updatable. */
@Entity
@Table(name = "spec_queue_events")
public class SpecQueueEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "item_id", updatable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private SpecQueueEventType eventType;

    @Column(nullable = false, updatable = false, length = 80)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 30)
    private SpecQueueItemStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 30)
    private SpecQueueItemStatus toStatus;

    @Column(name = "reason_code", updatable = false, length = 40)
    private String reasonCode;

    @Column(name = "workflow_id", updatable = false, length = 200)
    private String workflowId;

    @Column(name = "workflow_run_id", updatable = false, length = 100)
    private String workflowRunId;

    @Column(name = "planner_decision", updatable = false, length = 40)
    private String plannerDecision;

    @Column(name = "planner_version", updatable = false, length = 40)
    private String plannerVersion;

    @Column(name = "planner_override", updatable = false, length = 40)
    private String plannerOverride;

    @Column(name = "run_duration_ms", updatable = false)
    private Long runDurationMs;

    @Column(name = "review_iterations", updatable = false)
    private Integer reviewIterations;

    @Column(name = "approval_gate_iterations", updatable = false)
    private Integer approvalGateIterations;

    @Column(name = "approval_gate_outcome", updatable = false, length = 20)
    private String approvalGateOutcome;

    @Column(name = "merge_result", updatable = false, length = 40)
    private String mergeResult;

    @Column(name = "spec_file_size_bytes", updatable = false)
    private Integer specFileSizeBytes;

    @Column(name = "pr_count", updatable = false)
    private Integer prCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SpecQueueEvent() {
    }

    public SpecQueueEvent(UUID projectId, UUID itemId, SpecQueueEventType type, String actor, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.itemId = itemId;
        this.eventType = type;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public SpecQueueEvent withStatusChange(SpecQueueItemStatus from, SpecQueueItemStatus to) {
        this.fromStatus = from;
        this.toStatus = to;
        return this;
    }

    public SpecQueueEvent withReasonCode(String code) {
        this.reasonCode = code;
        return this;
    }

    public SpecQueueEvent withWorkflow(String workflowId, String runId) {
        this.workflowId = workflowId;
        this.workflowRunId = runId;
        return this;
    }

    public SpecQueueEvent withPlanner(String decision, String version, String override) {
        this.plannerDecision = decision;
        this.plannerVersion = version;
        this.plannerOverride = override;
        return this;
    }

    public SpecQueueEvent withRunMetrics(Long durationMs, Integer reviews, Integer gateIterations, String gateOutcome) {
        this.runDurationMs = durationMs;
        this.reviewIterations = reviews;
        this.approvalGateIterations = gateIterations;
        this.approvalGateOutcome = gateOutcome;
        return this;
    }

    public SpecQueueEvent withPublication(String mergeResult, Integer specSizeBytes, Integer prCount) {
        this.mergeResult = mergeResult;
        this.specFileSizeBytes = specSizeBytes;
        this.prCount = prCount;
        return this;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getItemId() { return itemId; }
    public SpecQueueEventType getEventType() { return eventType; }
    public String getActor() { return actor; }
    public SpecQueueItemStatus getFromStatus() { return fromStatus; }
    public SpecQueueItemStatus getToStatus() { return toStatus; }
    public String getReasonCode() { return reasonCode; }
    public String getWorkflowId() { return workflowId; }
    public String getWorkflowRunId() { return workflowRunId; }
    public String getPlannerDecision() { return plannerDecision; }
    public String getPlannerVersion() { return plannerVersion; }
    public String getPlannerOverride() { return plannerOverride; }
    public Long getRunDurationMs() { return runDurationMs; }
    public Integer getReviewIterations() { return reviewIterations; }
    public Integer getApprovalGateIterations() { return approvalGateIterations; }
    public String getApprovalGateOutcome() { return approvalGateOutcome; }
    public String getMergeResult() { return mergeResult; }
    public Integer getSpecFileSizeBytes() { return specFileSizeBytes; }
    public Integer getPrCount() { return prCount; }
    public Instant getCreatedAt() { return createdAt; }
}
