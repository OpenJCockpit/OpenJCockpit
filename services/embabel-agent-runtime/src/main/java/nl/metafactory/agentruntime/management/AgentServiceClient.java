package nl.metafactory.agentruntime.management;

import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import nl.metafactory.common.agentruntime.WorkflowRunDescriptor;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import nl.metafactory.common.oauth.ServiceRestClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The runtime's only way to management data: tenant bindings, active workflow runs and tool-call policy
 * decisions, all owned by {@code embabel-agent-service} and read over its internal API with the runtime's
 * service identity. A 404 means "does not exist"; every other failure (unconfigured, unreachable, denied,
 * malformed) is a {@link ManagementUnavailableException} so callers fail closed.
 */
public class AgentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceClient.class);

    private final RestClient rest;

    public AgentServiceClient(AgentServiceProperties properties) {
        this(create(properties));
    }

    AgentServiceClient(RestClient rest) {
        this.rest = rest;
    }

    private static RestClient create(AgentServiceProperties properties) {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return null;
        }
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config(properties.getTokenUrl(),
                properties.getClientId(), properties.getClientSecret()));
        return ServiceRestClients.create(properties.getBaseUrl(), tokens, properties.getConnectTimeout(),
                properties.getReadTimeout());
    }

    public boolean configured() {
        return rest != null;
    }

    public Optional<AgentTenantBinding> tenantBinding(String tenantId, String agentId) {
        return optional("tenant-binding", () -> rest.get().uri(AgentRuntimeApi.TENANT_BINDING, tenantId, agentId)
                .accept(MediaType.APPLICATION_JSON).retrieve().body(AgentTenantBinding.class));
    }

    public Optional<WorkflowRunDescriptor> workflowRun(String runId) {
        return optional("workflow-run", () -> rest.get().uri(AgentRuntimeApi.WORKFLOW_RUN, runId)
                .accept(MediaType.APPLICATION_JSON).retrieve().body(WorkflowRunDescriptor.class));
    }

    public ToolInvocationDecision decideToolInvocation(ToolInvocationDecision.Request request) {
        return call("tool-decision", () -> rest.post().uri(AgentRuntimeApi.TOOL_INVOCATION_DECISIONS)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(ToolInvocationDecision.class));
    }

    private <T> Optional<T> optional(String what, Supplier<T> call) {
        try {
            return Optional.of(call(what, call));
        } catch (ManagementUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException http && http.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private <T> T call(String what, Supplier<T> call) {
        if (rest == null) {
            throw new ManagementUnavailableException(what + ": agent service base URL is not configured", null);
        }
        T body;
        try {
            body = call.get();
        } catch (RuntimeException e) {
            // Never the exception message: it may echo URLs or response content.
            log.warn("agent-service.call.failed call={} error={}", what, e.getClass().getSimpleName());
            throw new ManagementUnavailableException(what + " failed: " + e.getClass().getSimpleName(), e);
        }
        if (body == null) {
            throw new ManagementUnavailableException(what + ": empty response", null);
        }
        return body;
    }

    /** The agent service could not answer; callers treat this as "not allowed / not available". */
    public static class ManagementUnavailableException extends RuntimeException {
        public ManagementUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
