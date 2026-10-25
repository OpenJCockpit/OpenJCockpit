package nl.metafactory.agentruntime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Who may use the runtime's internal API ({@code /internal/v1/**}). */
@Component
@ConfigurationProperties("openjcockpit.agent-runtime")
public class RuntimeApiProperties {

    /** Keycloak client (azp) of embabel-agent-service; the only caller of executions and drafts. */
    private String trustedClientId = "openjcockpit-agent-service";

    public String getTrustedClientId() {
        return trustedClientId;
    }

    public void setTrustedClientId(String trustedClientId) {
        this.trustedClientId = trustedClientId;
    }
}
