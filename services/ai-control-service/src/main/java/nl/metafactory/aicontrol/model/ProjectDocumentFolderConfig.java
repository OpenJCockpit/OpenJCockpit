package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The folder *name* is always the project's name (see Project.name) — this entity only stores the
 * base folder path it lives under, so renaming a project doesn't require migrating stored config.
 */
@Entity
@Table(name = "project_document_folder_config")
public class ProjectDocumentFolderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "folder_path", length = 500)
    private String folderPath;

    @Column(name = "file_trigger_enabled", nullable = false)
    private short fileTriggerEnabled = 0;

    @Column(name = "allowed_document_types", length = 500)
    private String allowedDocumentTypes;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(nullable = false)
    private short active = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public short getFileTriggerEnabled() { return fileTriggerEnabled; }
    public void setFileTriggerEnabled(short fileTriggerEnabled) { this.fileTriggerEnabled = fileTriggerEnabled; }

    public String getAllowedDocumentTypes() { return allowedDocumentTypes; }
    public void setAllowedDocumentTypes(String allowedDocumentTypes) { this.allowedDocumentTypes = allowedDocumentTypes; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public short getActive() { return active; }
    public void setActive(short active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
