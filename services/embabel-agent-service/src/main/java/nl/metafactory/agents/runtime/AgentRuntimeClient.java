package nl.metafactory.agents.runtime;

import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionResult;
import nl.metafactory.common.agentruntime.AgentExecutionStatus;
import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import nl.metafactory.common.oauth.ClientCredentialsTokens;
import nl.metafactory.common.oauth.ServiceRestClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * This service's only way to run agents and LLM calls: the internal API of {@code embabel-agent-runtime},
 * called with this service's client-credentials identity. Requests are never retried — an execution id is
 * accepted once by the runtime, and a timed-out run is reported, not repeated. Message and output text are
 * never logged.
 */
public class AgentRuntimeClient {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeClient.class);

    /** Reason of failures that happened between this service and the runtime (no answer from the runtime). */
    public static final String RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";

    private final RestClient executions;
    private final RestClient drafts;
    private final JsonMapper json = JsonMapper.builder().build();

    AgentRuntimeClient(RestClient executions, RestClient drafts) {
        this.executions = executions;
        this.drafts = drafts;
    }

    /** Unconfigured (blank base URL) yields a client whose every call fails as {@link #RUNTIME_UNAVAILABLE}. */
    public static AgentRuntimeClient create(AgentRuntimeProperties properties) {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return new AgentRuntimeClient(null, null);
        }
        var tokens = new ClientCredentialsTokens(new ClientCredentialsTokens.Config(properties.getTokenUrl(),
                properties.getClientId(), properties.getClientSecret()));
        return new AgentRuntimeClient(
                ServiceRestClients.create(properties.getBaseUrl(), tokens, properties.getConnectTimeout(),
                        properties.getExecutionTimeout()),
                ServiceRestClients.create(properties.getBaseUrl(), tokens, properties.getConnectTimeout(),
                        properties.getDraftTimeout()));
    }

    public boolean configured() {
        return executions != null;
    }

    /** Runs one agent and returns its answer; every failure is an {@link AgentRuntimeException}. */
    public AgentExecutionResult execute(AgentExecutionRequest request) {
        requireConfigured();
        try {
            return exchange(() -> executions.post().uri(AgentRuntimeApi.EXECUTIONS)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(AgentExecutionResult.class));
        } catch (AgentRuntimeException e) {
            if (RUNTIME_UNAVAILABLE.equals(e.reason()) && e.getCause() instanceof ResourceAccessException) {
                // The request may have reached the runtime; say whether it is still running, never resend it.
                String state = status(request.executionId()).map(s -> s.state().name()).orElse("UNKNOWN");
                log.warn("agent-runtime.execution.no-answer executionId={} runtimeState={}", request.executionId(), state);
                throw new AgentRuntimeException(RUNTIME_UNAVAILABLE, "No answer from the agent runtime (execution "
                        + request.executionId() + " is " + state + " there; it is not retried)", e);
            }
            throw e;
        }
    }

    /** The runtime's view of an execution; empty when unknown there or when the runtime cannot be asked. */
    public Optional<AgentExecutionStatus> status(String executionId) {
        if (!configured()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(drafts.get().uri(AgentRuntimeApi.EXECUTION, executionId)
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(AgentExecutionStatus.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** A definition draft of {@code kind} for {@code concept}, read as {@code type} (same JSON shape). */
    public <T> T draft(DefinitionDrafts.Kind kind, String concept, Class<T> type) {
        requireConfigured();
        return exchange(() -> drafts.post().uri(AgentRuntimeApi.DEFINITION_DRAFT, kind.pathSegment())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(new DefinitionDrafts.Request(concept)).retrieve().body(type));
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new AgentRuntimeException(RUNTIME_UNAVAILABLE,
                    "The agent runtime is not configured (openjcockpit.agent-runtime.base-url)", null);
        }
    }

    private <T> T exchange(Supplier<T> call) {
        T body;
        try {
            body = call.get();
        } catch (RestClientResponseException e) {
            var failure = failureOf(e);
            log.warn("agent-runtime.call.rejected status={} reason={}", e.getStatusCode().value(), failure.reason());
            throw new AgentRuntimeException(failure.reason(), failure.message(), e);
        } catch (RuntimeException e) {
            log.warn("agent-runtime.call.failed error={}", e.getClass().getSimpleName());
            throw new AgentRuntimeException(RUNTIME_UNAVAILABLE, "The agent runtime is unavailable ("
                    + e.getClass().getSimpleName() + ")", e);
        }
        if (body == null) {
            throw new AgentRuntimeException(RUNTIME_UNAVAILABLE, "The agent runtime returned no body", null);
        }
        return body;
    }

    /** The runtime's error body, or a generic failure for anything else (e.g. a proxy's error page). */
    private AgentExecutionFailure failureOf(RestClientResponseException e) {
        try {
            var failure = json.readValue(e.getResponseBodyAsByteArray(), AgentExecutionFailure.class);
            if (failure != null && failure.reason() != null && failure.message() != null) {
                return failure;
            }
        } catch (RuntimeException ignored) {
            // not the runtime's error body
        }
        return new AgentExecutionFailure(null, RUNTIME_UNAVAILABLE,
                "The agent runtime answered HTTP " + e.getStatusCode().value());
    }

    /** A request to the runtime that produced no result; {@code reason} is a stable code, the message is safe. */
    public static class AgentRuntimeException extends RuntimeException {

        private final String reason;

        public AgentRuntimeException(String reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
