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
 * Per-project configuration for the Hermes Agent signal integration. The signal listener itself is
 * not implemented yet — this only stores where/what to map a future incoming signal to a workflow.
 */
@Entity
@Table(name = "project_hermes_config")
public class ProjectHermesConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private short enabled = 0;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "encrypted_auth_token", columnDefinition = "TEXT")
    private String encryptedAuthToken;

    @Column(name = "signal_type")
    private String signalType;

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

    public short getEnabled() { return enabled; }
    public void setEnabled(short enabled) { this.enabled = enabled; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getEncryptedAuthToken() { return encryptedAuthToken; }
    public void setEncryptedAuthToken(String encryptedAuthToken) { this.encryptedAuthToken = encryptedAuthToken; }

    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public short getActive() { return active; }
    public void setActive(short active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
