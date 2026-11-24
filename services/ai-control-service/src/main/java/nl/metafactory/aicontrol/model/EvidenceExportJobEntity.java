package nl.metafactory.aicontrol.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Persistent state of one evidence export job (workflow or project scope). */
@Entity
@Table(name = "evidence_export_jobs")
public class EvidenceExportJobEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceExportScope scope;

    @Column(name = "workflow_id", length = 128)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceExportFormat format;

    @Column(name = "include_outputs", nullable = false)
    private boolean includeOutputs;

    @Column(name = "from_at")
    private Instant fromAt;

    @Column(name = "to_at")
    private Instant toAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceExportJobStatus status = EvidenceExportJobStatus.QUEUED;

    @Column(name = "runs_done", nullable = false)
    private int runsDone;

    @Column(name = "runs_total", nullable = false)
    private int runsTotal;

    @Column(length = 255)
    private String filename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(length = 1000)
    private String error;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public EvidenceExportScope getScope() { return scope; }
    public void setScope(EvidenceExportScope scope) { this.scope = scope; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public EvidenceExportFormat getFormat() { return format; }
    public void setFormat(EvidenceExportFormat format) { this.format = format; }
    public boolean isIncludeOutputs() { return includeOutputs; }
    public void setIncludeOutputs(boolean includeOutputs) { this.includeOutputs = includeOutputs; }
    public Instant getFromAt() { return fromAt; }
    public void setFromAt(Instant fromAt) { this.fromAt = fromAt; }
    public Instant getToAt() { return toAt; }
    public void setToAt(Instant toAt) { this.toAt = toAt; }
    public EvidenceExportJobStatus getStatus() { return status; }
    public int getRunsDone() { return runsDone; }
    public int getRunsTotal() { return runsTotal; }
    public String getFilename() { return filename; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getFilePath() { return filePath; }
    public String getError() { return error; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getExpiresAt() { return expiresAt; }
}
