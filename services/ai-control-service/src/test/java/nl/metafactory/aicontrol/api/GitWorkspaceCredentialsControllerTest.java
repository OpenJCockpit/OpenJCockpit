package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.config.GitWorkspaceProperties;
import nl.metafactory.aicontrol.service.GitWorkspaceCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitWorkspaceCredentialsControllerTest {

    private static final String REPO = "https://github.com/org/repo.git";

    private GitWorkspaceCredentialService service;
    private GitWorkspaceCredentialsController controller;

    @BeforeEach
    void setUp() {
        service = mock(GitWorkspaceCredentialService.class);
        controller = new GitWorkspaceCredentialsController(service, new GitWorkspaceProperties());
    }

    private static Jwt token(String azp) {
        var builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }

    @Test
    void theGitMcpServerGetsTheCredentialsWithoutCaching() {
        when(service.credentialsFor("job-1", REPO)).thenReturn(Optional.of(
                new GitWorkspaceCredentialService.WorkspaceCredentials("bot", "t0k")));

        var response = controller.credentials("job-1", REPO, token("openjcockpit-git-mcp"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("username", "bot").containsEntry("token", "t0k");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void anyOtherCallerIsForbiddenBeforeAnyLookup() {
        assertThat(controller.credentials("job-1", REPO, token("dashboard")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.credentials("job-1", REPO, token("openjcockpit-ai-control")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.credentials("job-1", REPO, token(null)).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.credentials("job-1", REPO, null).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(service);
    }

    @Test
    void anInactiveOrForeignWorkspaceIsNotFound() {
        when(service.credentialsFor(any(), any())).thenReturn(Optional.empty());

        assertThat(controller.credentials("job-2", REPO, token("openjcockpit-git-mcp")).getStatusCode().value()).isEqualTo(404);
    }
}
