package nl.metafactory.agentruntime.execution;

import nl.metafactory.agentruntime.config.RuntimeApiProperties;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentExecutionRequest;
import nl.metafactory.common.agentruntime.AgentExecutionResult;
import nl.metafactory.common.agentruntime.AgentExecutionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionControllerTest {

    private final AgentExecutionService executions = mock(AgentExecutionService.class);
    private final ExecutionRegistry registry = new ExecutionRegistry();
    private final RuntimeApiProperties properties = new RuntimeApiProperties();
    private final AgentExecutionController controller = new AgentExecutionController(executions, registry, properties);
    private final AgentExecutionRequest request =
            new AgentExecutionRequest("e-1", "local", "qa", "alice", null, null, "msg", null);

    private static Jwt caller(String azp) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("azp", azp)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void onlyTheAgentServiceMayRunAgentsOrReadExecutions() {
        var response = controller.execute(request, caller("openjcockpit-a2a"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(((AgentExecutionFailure) response.getBody()).reason()).isEqualTo(AgentExecutionFailure.FORBIDDEN);
        assertThat(controller.status("e-1", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(executions);

        properties.setTrustedClientId("custom-service");
        assertThat(properties.getTrustedClientId()).isEqualTo("custom-service");
        assertThat(controller.execute(request, caller("openjcockpit-agent-service")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAnswerIsReturnedUncachedAndAFailureKeepsItsStatusAndReason() {
        var result = new AgentExecutionResult("e-1", "qa", "1", "h", "t", "answer");
        when(executions.execute(any())).thenReturn(result)
                .thenThrow(new AgentExecutionException(HttpStatus.CONFLICT, AgentExecutionFailure.DUPLICATE_EXECUTION, "dup"));

        var ok = controller.execute(request, caller("openjcockpit-agent-service"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isEqualTo(result);
        assertThat(ok.getHeaders().getCacheControl()).isEqualTo("no-store");

        var duplicate = controller.execute(request, caller("openjcockpit-agent-service"));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isEqualTo(new AgentExecutionFailure("e-1", AgentExecutionFailure.DUPLICATE_EXECUTION, "dup"));
    }

    @Test
    void theStatusOfAKnownExecutionIsReadableAndAnUnknownOneIs404() {
        registry.begin("e-1");

        var known = controller.status("e-1", caller("openjcockpit-agent-service"));
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((AgentExecutionStatus) known.getBody()).state()).isEqualTo(AgentExecutionStatus.State.RUNNING);

        var unknown = controller.status("e-x", caller("openjcockpit-agent-service"));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((AgentExecutionFailure) unknown.getBody()).reason()).isEqualTo(AgentExecutionFailure.UNKNOWN_EXECUTION);
    }
}
