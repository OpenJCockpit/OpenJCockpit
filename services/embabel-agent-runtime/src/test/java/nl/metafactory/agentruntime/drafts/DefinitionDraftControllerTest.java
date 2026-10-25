package nl.metafactory.agentruntime.drafts;

import nl.metafactory.agentruntime.config.RuntimeApiProperties;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefinitionDraftControllerTest {

    private final DefinitionDraftService drafts = mock(DefinitionDraftService.class);
    private final DefinitionDraftController controller = new DefinitionDraftController(drafts, new RuntimeApiProperties());

    private static Jwt caller(String azp) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("azp", azp)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void onlyTheAgentServiceMayAskForDrafts() {
        var response = controller.draft("agent", new DefinitionDrafts.Request("x"), caller("dashboard"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(drafts);
    }

    @Test
    void unknownKindsAndMissingOrHugeConceptsAreBadRequests() {
        var service = caller("openjcockpit-agent-service");
        for (var bad : new Object[][]{{"workflow", "x"}, {"agent", null}, {"agent", " "},
                {"agent", "x".repeat(DefinitionDraftController.MAX_CONCEPT_CHARS + 1)}}) {
            var response = controller.draft((String) bad[0], new DefinitionDrafts.Request((String) bad[1]), service);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(((AgentExecutionFailure) response.getBody()).reason()).isEqualTo(AgentExecutionFailure.INVALID_REQUEST);
        }
        verifyNoInteractions(drafts);
    }

    @Test
    void aDraftIsReturnedAndAnLlmFailureIsABadGateway() {
        var draft = new DefinitionDrafts.SkillDraft("k", null, null, null, null, null, null);
        when(drafts.draft(any(), any())).thenReturn(draft).thenThrow(new IllegalStateException("model down"));
        var service = caller("openjcockpit-agent-service");

        var ok = controller.draft("skill", new DefinitionDrafts.Request("classify"), service);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isSameAs(draft);

        var failed = controller.draft("skill", new DefinitionDrafts.Request("classify"), service);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(((AgentExecutionFailure) failed.getBody()).message()).doesNotContain("model down");
    }
}
