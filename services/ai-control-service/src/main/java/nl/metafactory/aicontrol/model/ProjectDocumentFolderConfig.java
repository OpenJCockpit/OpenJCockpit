package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The folder *name* is always the project's name (see Project.name) — this entity only stores the
 * base folder path it lives under, so renaming a project doesn't require migrating stored config.
 */
@Entity
@Table(name = "project_document_folder_config")
public class ProjectDocumentFolderConfig extends ProjectScopedEntity {

    @Column(name = "folder_path", length = 500)
    private String folderPath;

    @Column(name = "file_trigger_enabled", nullable = false)
    private short fileTriggerEnabled = 0;

    @Column(name = "allowed_document_types", length = 500)
    private String allowedDocumentTypes;

    @Column(name = "workflow_id")
    private String workflowId;

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public short getFileTriggerEnabled() { return fileTriggerEnabled; }
    public void setFileTriggerEnabled(short fileTriggerEnabled) { this.fileTriggerEnabled = fileTriggerEnabled; }

    public String getAllowedDocumentTypes() { return allowedDocumentTypes; }
    public void setAllowedDocumentTypes(String allowedDocumentTypes) { this.allowedDocumentTypes = allowedDocumentTypes; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
}
