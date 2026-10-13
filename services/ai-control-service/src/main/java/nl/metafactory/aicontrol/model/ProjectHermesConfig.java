package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Per-project configuration for the Hermes Agent signal integration. The signal listener itself is
 * not implemented yet — this only stores where/what to map a future incoming signal to a workflow.
 */
@Entity
@Table(name = "project_hermes_config")
public class ProjectHermesConfig extends ProjectScopedEntity {

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
}
