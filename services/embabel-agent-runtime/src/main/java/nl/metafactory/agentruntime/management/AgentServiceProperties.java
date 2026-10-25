package nl.metafactory.agentruntime.management;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Connection of the runtime to {@code embabel-agent-service}, the owner of tenant bindings, workflow runs
 * and policy decisions. The base URL is configuration only ({@code EMBABEL_AGENT_SERVICE_BASE_URL}): a
 * compose service name locally, any HTTPS endpoint when the runtime runs on another host.
 */
@Component
@ConfigurationProperties("openjcockpit.agent-service")
public class AgentServiceProperties {

    /** Base URL of embabel-agent-service; blank means unconfigured, so every lookup fails closed. */
    private String baseUrl;

    /** Keycloak token endpoint of the runtime's own service identity. */
    private String tokenUrl;

    /** Keycloak client (azp) of the runtime; the agent service only answers this identity. */
    private String clientId = "openjcockpit-agent-runtime";

    private String clientSecret;

    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Lookups are small and on the hot path of every run and tool call. */
    private Duration readTimeout = Duration.ofSeconds(15);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    @Override
    public String toString() {
        return "AgentServiceProperties{baseUrl=" + baseUrl + ", clientId=" + clientId + ", clientSecret=***}";
    }
}
