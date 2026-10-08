package nl.metafactory.aicontrol.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "git_workspace_jobs")
public class GitWorkspaceJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "spec_file_ref", nullable = false, length = 500)
    private String specFileRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GitWorkspaceJobStatus status = GitWorkspaceJobStatus.CREATED;

    @Column(name = "base_branch", length = 100)
    private String baseBranch;

    @Column(name = "working_branch", length = 255)
    private String workingBranch;

    @Column(name = "commit_hash", length = 64)
    private String commitHash;

    @Column(name = "container_id", length = 128)
    private String containerId;

    @Column(name = "workspace_path", length = 500)
    private String workspacePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 50)
    private GitWorkspaceJobErrorCode errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_by", length = 255)
    private String startedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getSpecFileRef() { return specFileRef; }
    public void setSpecFileRef(String specFileRef) { this.specFileRef = specFileRef; }
    public GitWorkspaceJobStatus getStatus() { return status; }
    public void setStatus(GitWorkspaceJobStatus status) { this.status = status; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getWorkingBranch() { return workingBranch; }
    public void setWorkingBranch(String workingBranch) { this.workingBranch = workingBranch; }
    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public GitWorkspaceJobErrorCode getErrorCode() { return errorCode; }
    public void setErrorCode(GitWorkspaceJobErrorCode errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}