package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/** An {@link AuditedEntity} that belongs to a project. */
@MappedSuperclass
public abstract class ProjectScopedEntity extends AuditedEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
}
