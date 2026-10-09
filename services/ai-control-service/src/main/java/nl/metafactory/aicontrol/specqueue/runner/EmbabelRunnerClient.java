package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.client.WorkflowStartResponseDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.CodecException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Calls the agent service as the runner service identity. Only the operation name and a fixed failure kind
 * are logged. A start is never retried; "not processed" is claimed only when provably true.
 */
@Component
public class EmbabelRunnerClient {

    private static final Logger log = LoggerFactory.getLogger(EmbabelRunnerClient.class);
    private static final int MAX_REASON = 500;

    public enum FailureCode { NOT_FOUND, UNAUTHORIZED, FORBIDDEN, SERVER_ERROR, TRANSPORT, DECODE }

    public enum NotSentReason { UNCONFIGURED, UNAUTHORIZED, FORBIDDEN, CONNECT_FAILED }

    public enum AmbiguousReason { TIMEOUT, TRANSPORT, SERVER_ERROR, DECODE, UNEXPECTED_STATUS }

    public sealed interface StartResult {
        record Started(String runId, String status, Instant startedAt) implements StartResult {}

        record Rejected(int httpStatus, String reason) implements StartResult {}

        record NotSent(NotSentReason reason) implements StartResult {}

        record Ambiguous(AmbiguousReason reason) implements StartResult {}
    }

    public sealed interface RunLookup {
        record Found(AgentRunDto run) implements RunLookup {}

        record Unavailable(FailureCode code) implements RunLookup {}
    }

    private enum FailureKind { UNCONFIGURED, DECODE, CONNECT, TIMEOUT, TRANSPORT }

    private record Raw<T>(int status, T body) {}

    private static final class Failure extends RuntimeException {
        private final FailureKind kind;

        Failure(FailureKind kind) {
            super(kind.name(), null, false, false);
            this.kind = kind;
        }
    }

    private final WebClient webClient;
    private final Duration timeout;

    public EmbabelRunnerClient(@Qualifier("embabelRunnerWebClient") WebClient webClient,
                               SpecQueueProperties properties) {
        this.webClient = webClient;
        this.timeout = properties.getRunner().getEmbabelTimeout();
    }

    public StartResult startWorkflow(String workflowId, WorkflowStartInputDto input) {
        Raw<WorkflowStartResponseDto> raw;
        try {
            raw = exchange("startWorkflow", webClient.post().uri("/api/workflows/{id}/start", workflowId)
                    .bodyValue(input != null ? input : WorkflowStartInputDto.empty()), WorkflowStartResponseDto.class);
        } catch (Failure f) {
            return switch (f.kind) {
                case UNCONFIGURED -> new StartResult.NotSent(NotSentReason.UNCONFIGURED);
                case CONNECT -> new StartResult.NotSent(NotSentReason.CONNECT_FAILED);
                case TIMEOUT -> new StartResult.Ambiguous(AmbiguousReason.TIMEOUT);
                case DECODE -> new StartResult.Ambiguous(AmbiguousReason.DECODE);
                case TRANSPORT -> new StartResult.Ambiguous(AmbiguousReason.TRANSPORT);
            };
        }
        int status = raw.status();
        if (status >= 200 && status < 300) {
            WorkflowStartResponseDto body = raw.body();
            if (body == null) {
                return new StartResult.Ambiguous(AmbiguousReason.DECODE);
            }
            if (body.status() != null && body.status().strip().equalsIgnoreCase("BLOCKED")) {
                return new StartResult.Rejected(status, truncate(body.message()));
            }
            if (body.executionId() == null || body.executionId().isBlank()) {
                return new StartResult.Ambiguous(AmbiguousReason.DECODE);
            }
            return new StartResult.Started(body.executionId(), body.status(), body.startedAt());
        }
        if (status == 401) return new StartResult.NotSent(NotSentReason.UNAUTHORIZED);
        if (status == 403) return new StartResult.NotSent(NotSentReason.FORBIDDEN);
        if (status >= 400 && status < 500) return new StartResult.Rejected(status, null);
        if (status >= 500) return new StartResult.Ambiguous(AmbiguousReason.SERVER_ERROR);
        return new StartResult.Ambiguous(AmbiguousReason.UNEXPECTED_STATUS);
    }

    /** Never throws. */
    public RunLookup getRun(String runId) {
        try {
            return new RunLookup.Found(read("getRun", "/api/agent-runs/{id}", runId, AgentRunDto.class));
        } catch (UpstreamUnavailableException e) {
            return new RunLookup.Unavailable(e.code());
        }
    }

    public Optional<WorkflowDefinitionDto> getWorkflow(String id) {
        return readOptional("getWorkflow", "/api/workflows/{id}", id, WorkflowDefinitionDto.class);
    }

    public Optional<WorkflowGroupDto> getWorkflowGroup(String id) {
        return readOptional("getWorkflowGroup", "/api/workflow-groups/{id}", id, WorkflowGroupDto.class);
    }

    private <T> Optional<T> readOptional(String operation, String path, String id, Class<T> type) {
        try {
            return Optional.of(read(operation, path, id, type));
        } catch (UpstreamUnavailableException e) {
            if (e.code() == FailureCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private <T> T read(String operation, String path, String id, Class<T> type) {
        Raw<T> raw;
        try {
            raw = exchange(operation, webClient.get().uri(path, id), type);
        } catch (Failure f) {
            throw new UpstreamUnavailableException(switch (f.kind) {
                case UNCONFIGURED -> FailureCode.UNAUTHORIZED;
                case DECODE -> FailureCode.DECODE;
                case CONNECT, TIMEOUT, TRANSPORT -> FailureCode.TRANSPORT;
            });
        }
        int status = raw.status();
        if (status >= 200 && status < 300) {
            if (raw.body() == null) throw new UpstreamUnavailableException(FailureCode.DECODE);
            return raw.body();
        }
        throw new UpstreamUnavailableException(switch (status) {
            case 404 -> FailureCode.NOT_FOUND;
            case 401 -> FailureCode.UNAUTHORIZED;
            case 403 -> FailureCode.FORBIDDEN;
            default -> FailureCode.SERVER_ERROR;
        });
    }

    private <T> Raw<T> exchange(String operation, WebClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            return request.exchangeToMono(response -> {
                int status = response.statusCode().value();
                if (response.statusCode().is2xxSuccessful()) {
                    return response.bodyToMono(type).map(body -> new Raw<>(status, body))
                            .defaultIfEmpty(new Raw<>(status, null));
                }
                return response.releaseBody().thenReturn(new Raw<T>(status, null));
            }).timeout(timeout).block();
        } catch (RuntimeException e) {
            FailureKind kind = classify(e);
            log.warn("Embabel runner request failed: operation={} failure={}", operation, kind);
            throw new Failure(kind);
        }
    }

    private static FailureKind classify(Throwable failure) {
        Throwable t = failure;
        for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
            if (t instanceof RunnerIdentityUnconfiguredException) return FailureKind.UNCONFIGURED;
            if (t instanceof CodecException) return FailureKind.DECODE;
            if (t instanceof ConnectException || t instanceof UnknownHostException) return FailureKind.CONNECT;
            if (t instanceof java.util.concurrent.TimeoutException || t instanceof io.netty.handler.timeout.TimeoutException) {
                return FailureKind.TIMEOUT;
            }
        }
        return FailureKind.TRANSPORT;
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() <= MAX_REASON ? s : s.substring(0, MAX_REASON);
    }
}
