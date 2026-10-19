package nl.metafactory.aicontrol.specqueue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spec_queue_items")
public class SpecQueueItem {

    private static final Short ACTIVE_SLOT = 1;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "spec_file", nullable = false, length = 500)
    private String specFile;

    @Column(name = "workflow_id", nullable = false, length = 200)
    private String workflowId;

    @Column(name = "workflow_name", length = 255)
    private String workflowName;

    @Column(name = "auto_merge", nullable = false)
    private boolean autoMerge;

    @Column(nullable = false)
    private long position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SpecQueueItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private SpecQueueFailureReason failureReason;

    @Column(name = "workflow_run_id", length = 100)
    private String workflowRunId;

    @Column(name = "last_run_status", length = 40)
    private String lastRunStatus;

    @Column(name = "start_claimed_at")
    private Instant startClaimedAt;

    @Column(name = "merge_attempt_started_at")
    private Instant mergeAttemptStartedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "merge_head_sha", length = 64)
    private String mergeHeadSha;

    @Column(name = "spec_file_size_bytes")
    private Integer specFileSizeBytes;

    @Column(name = "open_spec_key", length = 500)
    private String openSpecKey;

    @Column(name = "active_slot")
    private Short activeSlot;

    @Column(name = "created_by_sub", nullable = false, updatable = false, length = 80)
    private String createdBySub;

    @Column(name = "created_by_username", updatable = false, length = 255)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected SpecQueueItem() {
    }

    public SpecQueueItem(UUID projectId, String specFile, String workflowId, String workflowName,
                         boolean autoMerge, long position, String createdBySub,
                         String createdByUsername, Instant createdAt) {
        this(projectId, specFile, workflowId, workflowName, autoMerge, position, null,
                createdBySub, createdByUsername, createdAt);
    }

    public SpecQueueItem(UUID projectId, String specFile, String workflowId, String workflowName,
                         boolean autoMerge, long position, Integer specFileSizeBytes, String createdBySub,
                         String createdByUsername, Instant createdAt) {
        this.specFileSizeBytes = specFileSizeBytes;
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.specFile = specFile;
        this.workflowId = workflowId;
        this.workflowName = workflowName;
        this.autoMerge = autoMerge;
        this.position = position;
        this.createdBySub = createdBySub;
        this.createdByUsername = createdByUsername;
        this.createdAt = createdAt;
        applyStatus(SpecQueueItemStatus.QUEUED, createdAt);
    }

    /** Every status change keeps the derived columns in step: the DB constraints depend on them. */
    private void applyStatus(SpecQueueItemStatus newStatus, Instant at) {
        this.status = newStatus;
        this.activeSlot = newStatus.isActive() ? ACTIVE_SLOT : null;
        this.openSpecKey = (newStatus.isOpen() || newStatus == SpecQueueItemStatus.FAILED) ? specFile : null;
        this.updatedAt = at;
    }

    public void transitionTo(SpecQueueItemStatus newStatus, Instant at) {
        applyStatus(newStatus, at);
    }

    public void reassign(String newSpecFile, String newWorkflowId, String newWorkflowName,
                         boolean newAutoMerge, Integer newSpecFileSizeBytes, Instant at) {
        this.specFile = newSpecFile;
        this.workflowId = newWorkflowId;
        this.workflowName = newWorkflowName;
        this.autoMerge = newAutoMerge;
        this.specFileSizeBytes = newSpecFileSizeBytes;
        applyStatus(this.status, at);
    }

    public void moveTo(long newPosition, Instant at) {
        this.position = newPosition;
        this.updatedAt = at;
    }

    public void recordFailureReason(SpecQueueFailureReason reason, Instant at) {
        this.failureReason = reason;
        this.updatedAt = at;
    }

    public void recordStartClaim(Instant at) {
        this.startClaimedAt = at;
        this.updatedAt = at == null ? this.updatedAt : at;
    }

    public void recordRun(String runId, Instant runStartedAt) {
        this.workflowRunId = runId;
        this.startedAt = runStartedAt;
        if (runStartedAt != null) {
            this.updatedAt = runStartedAt;
        }
    }

    public void recordLastRunStatus(String runStatus, Instant at) {
        this.lastRunStatus = runStatus;
        this.updatedAt = at;
    }

    public void recordMergeAttempt(Instant attemptStartedAt, String headSha) {
        this.mergeAttemptStartedAt = attemptStartedAt;
        this.mergeHeadSha = headSha;
        if (attemptStartedAt != null) {
            this.updatedAt = attemptStartedAt;
        }
    }

    public void recordCancelRequest(Instant at) {
        this.cancelRequestedAt = at;
        if (at != null) {
            this.updatedAt = at;
        }
    }

    public void recordFinished(Instant at) {
        this.finishedAt = at;
        if (at != null) {
            this.updatedAt = at;
        }
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getSpecFile() { return specFile; }
    public String getWorkflowId() { return workflowId; }
    public String getWorkflowName() { return workflowName; }
    public boolean isAutoMerge() { return autoMerge; }
    public long getPosition() { return position; }
    public SpecQueueItemStatus getStatus() { return status; }
    public SpecQueueFailureReason getFailureReason() { return failureReason; }
    public String getWorkflowRunId() { return workflowRunId; }
    public String getLastRunStatus() { return lastRunStatus; }
    public Instant getStartClaimedAt() { return startClaimedAt; }
    public Instant getMergeAttemptStartedAt() { return mergeAttemptStartedAt; }
    public Instant getCancelRequestedAt() { return cancelRequestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getMergeHeadSha() { return mergeHeadSha; }
    public Integer getSpecFileSizeBytes() { return specFileSizeBytes; }
    public String getOpenSpecKey() { return openSpecKey; }
    public Short getActiveSlot() { return activeSlot; }
    public String getCreatedBySub() { return createdBySub; }
    public String getCreatedByUsername() { return createdByUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
