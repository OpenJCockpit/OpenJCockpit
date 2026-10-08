package nl.metafactory.agents.policy.config;

import nl.metafactory.agents.policy.model.FailMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Global Open Policy Agent configuration. There is one OPA deployment per environment (see the
 * OPA_BASE_URL examples in the workflow configuration docs) — this is deliberately a single
 * mutable properties bean, not a per-project row: the Workflow Configuration UI's Save action
 * updates these fields at runtime via OpaConfigController, but edits revert to the env-var
 * defaults below on service restart.
 */
@Component
@ConfigurationProperties("openjcockpit.opa")
public class OpaProperties {

    private boolean enabled = false;
    private String baseUrl = "";
    private String policyPath = "/v1/data/openjcockpit/workflow/decision";
    private String healthPath = "/health";
    private int timeoutSeconds = 3;
    private FailMode failMode = FailMode.FAIL_CLOSED;
    private boolean decisionLoggingEnabled = true;
    private boolean decisionLogExportEnabled = true;
    private String environment = "";
    private String customerLabel = "";
    private String projectLabel = "";
    private String authToken = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getPolicyPath() { return policyPath; }
    public void setPolicyPath(String policyPath) { this.policyPath = policyPath; }

    public String getHealthPath() { return healthPath; }
    public void setHealthPath(String healthPath) { this.healthPath = healthPath; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public FailMode getFailMode() { return failMode; }
    public void setFailMode(FailMode failMode) { this.failMode = failMode; }

    public boolean isDecisionLoggingEnabled() { return decisionLoggingEnabled; }
    public void setDecisionLoggingEnabled(boolean decisionLoggingEnabled) { this.decisionLoggingEnabled = decisionLoggingEnabled; }

    public boolean isDecisionLogExportEnabled() { return decisionLogExportEnabled; }
    public void setDecisionLogExportEnabled(boolean decisionLogExportEnabled) { this.decisionLogExportEnabled = decisionLogExportEnabled; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getCustomerLabel() { return customerLabel; }
    public void setCustomerLabel(String customerLabel) { this.customerLabel = customerLabel; }

    public String getProjectLabel() { return projectLabel; }
    public void setProjectLabel(String projectLabel) { this.projectLabel = projectLabel; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
}
