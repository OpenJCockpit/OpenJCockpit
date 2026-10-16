package nl.metafactory.agents.a2a;

import com.embabel.agent.api.validation.guardrails.GuardRailViolationException;
import com.embabel.common.core.validation.ValidationSeverity;
import nl.metafactory.agents.definition.config.AgentDefinitionProperties;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How message/send turns parts into one prompt and agent failures into JSON-RPC errors. */
class A2AInboundAdapterTest {

    private AgentRunService runs;
    private A2AInboundAdapter adapter;

    @BeforeEach
    void setUp() {
        runs = mock(AgentRunService.class);
        adapter = new A2AInboundAdapter(runs, new AgentDefinitionProperties(), new WorkflowRunScopes(),
                new GitWorkspaceProperties());
    }

    private static Jwt token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("svc").claim("tenant_id", "local")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static String send(String... texts) {
        var parts = new StringBuilder();
        for (String t : texts) {
            parts.append(parts.isEmpty() ? "" : ",").append("{\"kind\":\"text\",\"text\":\"").append(t).append("\"}");
        }
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":{\"message\":{\"kind\":\"message\","
                + "\"role\":\"user\",\"messageId\":\"m1\",\"parts\":[" + parts + "]}}}";
    }

    private JsonNode handle(String body) {
        return adapter.handle("qa-engineer", body, token(), null, null);
    }

    @Test
    void textPartsAreJoinedIntoOnePrompt() {
        when(runs.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ConfiguredDefinitionAgent.Response("qa-engineer", "1", "h", "t", "done"));

        handle(send("first", "second"));

        verify(runs).run(eq("local"), eq("qa-engineer"), any(), any(), eq("first\nsecond"), any());
    }

    @Test
    void aTooLongMessageIsRejectedBeforeTheAgentRuns() {
        var reply = handle(send("x".repeat(A2AProtocol.MAX_MESSAGE_CHARS + 1)));

        assertThat(reply.path("error").path("message").asString()).isEqualTo("Message too long");
        verify(runs, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anUnavailableDefinitionIsATransientInternalError() {
        when(runs.run(any(), any(), any(), any(), any(), any()))
                .thenThrow(new AgentResolutionException(ResolutionFailure.UNAVAILABLE, "LiteLLM down"));

        var error = handle(send("hi")).path("error");

        assertThat(error.path("code").asInt()).isEqualTo(A2AProtocol.INTERNAL_ERROR);
        assertThat(error.path("data").path("reason").asString()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void aGuardrailBlockAndAnUnexpectedFailureNeverEchoTheirDetails() {
        when(runs.run(any(), any(), any(), any(), any(), any()))
                .thenThrow(new GuardRailViolationException("pii-protection", "BSN 111222333", ValidationSeverity.CRITICAL))
                .thenThrow(new IllegalStateException("secret internals"));

        var blocked = handle(send("hi")).path("error");
        var failed = handle(send("hi")).path("error");

        assertThat(blocked.path("data").path("reason").asString()).isEqualTo("GUARDRAIL_VIOLATION");
        assertThat(blocked.toString()).doesNotContain("111222333");
        assertThat(failed.path("code").asInt()).isEqualTo(A2AProtocol.INTERNAL_ERROR);
        assertThat(failed.toString()).doesNotContain("secret internals");
    }
}
