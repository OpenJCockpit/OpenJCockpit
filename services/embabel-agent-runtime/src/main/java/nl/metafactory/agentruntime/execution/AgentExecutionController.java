package nl.metafactory.agentruntime.execution;

import nl.metafactory.agentruntime.config.RuntimeApiProperties;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import nl.metafactory.common.security.ServiceCallers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal execution API for the workflow engine of {@code embabel-agent-service}. Only that service's
 * identity ({@code azp}) may call it; every other caller — a user, an A2A client, another service — gets
 * 403. Synchronous by design: one request runs one agent and returns its answer, exactly like the in-process
 * call it replaces; the engine's own thread waits, as before.
 */
@RestController
@ConditionalOnProperty(name = "openjcockpit.agent-definitions.enabled", havingValue = "true")
public class AgentExecutionController {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionController.class);

    private final AgentExecutionService executions;
    private final ExecutionRegistry registry;
    private final RuntimeApiProperties properties;

    public AgentExecutionController(AgentExecutionService executions, ExecutionRegistry registry,
                                    RuntimeApiProperties properties) {
        this.executions = executions;
        this.registry = registry;
        this.properties = properties;
    }

    @PostMapping(path = AgentRuntimeApi.EXECUTIONS, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> execute(@RequestBody AgentExecutionRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (!ServiceCallers.is(jwt, properties.getTrustedClientId())) {
            return forbidden(request.executionId(), jwt);
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(executions.execute(request));
        } catch (AgentExecutionException e) {
            return ResponseEntity.status(e.status())
                    .body(new AgentExecutionFailure(request.executionId(), e.reason(), e.getMessage()));
        }
    }

    @GetMapping(path = AgentRuntimeApi.EXECUTION, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(@PathVariable String executionId, @AuthenticationPrincipal Jwt jwt) {
        if (!ServiceCallers.is(jwt, properties.getTrustedClientId())) {
            return forbidden(executionId, jwt);
        }
        return registry.find(executionId).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AgentExecutionFailure(
                        executionId, AgentExecutionFailure.UNKNOWN_EXECUTION, "Unknown execution")));
    }

    private static ResponseEntity<AgentExecutionFailure> forbidden(String executionId, Jwt jwt) {
        log.warn("agent.execution.denied reason=caller-not-allowed caller={}", ServiceCallers.clientId(jwt));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AgentExecutionFailure(executionId, AgentExecutionFailure.FORBIDDEN, "Caller not permitted"));
    }
}
