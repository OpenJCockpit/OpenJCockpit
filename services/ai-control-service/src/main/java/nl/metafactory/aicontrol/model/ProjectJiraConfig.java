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

@Entity
@Table(name = "project_jira_config")
public class ProjectJiraConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private short enabled = 0;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "project_key", length = 50)
    private String projectKey;

    @Column(name = "encrypted_auth_token", columnDefinition = "TEXT")
    private String encryptedAuthToken;

    @Column(name = "issue_type_mapping")
    private String issueTypeMapping;

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

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getEncryptedAuthToken() { return encryptedAuthToken; }
    public void setEncryptedAuthToken(String encryptedAuthToken) { this.encryptedAuthToken = encryptedAuthToken; }

    public String getIssueTypeMapping() { return issueTypeMapping; }
    public void setIssueTypeMapping(String issueTypeMapping) { this.issueTypeMapping = issueTypeMapping; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public short getActive() { return active; }
    public void setActive(short active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
