package nl.metafactory.agents.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Where and as whom this service reaches {@code embabel-agent-runtime}, the execution plane that runs every
 * agent, and which runtime identity may read this service's management API. The base URL is configuration
 * only ({@code EMBABEL_AGENT_RUNTIME_BASE_URL}): the compose service name locally, any host or HTTPS URL
 * elsewhere.
 */
@Component
@ConfigurationProperties("openjcockpit.agent-runtime")
public class AgentRuntimeProperties {

    /** Base URL of embabel-agent-runtime; blank means local agents and drafts are unavailable. */
    private String baseUrl;

    /** Keycloak token endpoint of this service's identity towards the runtime. */
    private String tokenUrl;

    /** Keycloak client (azp) of this service; the runtime only runs agents for this identity. */
    private String clientId = "openjcockpit-agent-service";

    private String clientSecret;

    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * How long one agent execution may take. Agent runs are synchronous and long (minutes with a local model
     * and tool calls); a request is never retried, so this only bounds the wait.
     */
    private Duration executionTimeout = Duration.ofMinutes(60);

    /** How long one definition draft (a single LLM call) may take. */
    private Duration draftTimeout = Duration.ofMinutes(10);

    /** Keycloak client (azp) of the runtime: the only caller of {@code /internal/agent-runtime/v1/**}. */
    private String runtimeClientId = "openjcockpit-agent-runtime";

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
    public Duration getExecutionTimeout() { return executionTimeout; }
    public void setExecutionTimeout(Duration executionTimeout) { this.executionTimeout = executionTimeout; }
    public Duration getDraftTimeout() { return draftTimeout; }
    public void setDraftTimeout(Duration draftTimeout) { this.draftTimeout = draftTimeout; }
    public String getRuntimeClientId() { return runtimeClientId; }
    public void setRuntimeClientId(String runtimeClientId) { this.runtimeClientId = runtimeClientId; }

    @Override
    public String toString() {
        return "AgentRuntimeProperties{baseUrl=" + baseUrl + ", clientId=" + clientId + ", clientSecret=***}";
    }
}
