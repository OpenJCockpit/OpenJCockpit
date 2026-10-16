package nl.metafactory.gitmcp.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiControlGitCredentialResolverTest {

    private static final String REPO = "https://github.com/org/repo.git";

    private MockRestServiceServer server;
    private AiControlGitCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        var config = new GitToolsProperties.Credentials();
        config.setAiControlUrl("http://ai-control:8080/");
        config.setTokenUrl("http://keycloak/token");
        config.setClientId("openjcockpit-git-mcp");
        config.setClientSecret("client-secret");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        resolver = new AiControlGitCredentialResolver(config, builder.build(),
                Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC));
    }

    private void expectServiceToken() {
        server.expect(requestTo("http://keycloak/token")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"svc\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    @Test
    void fetchesTheJobCredentialsWithTheServiceIdentity() {
        expectServiceToken();
        server.expect(requestTo("http://ai-control:8080/internal/git-workspaces/job-1/credentials?repositoryUrl=https%3A%2F%2Fgithub.com%2Forg%2Frepo.git"))
                .andExpect(header("Authorization", "Bearer svc"))
                .andRespond(withSuccess("{\"username\":\"bot\",\"token\":\"t0k\"}", MediaType.APPLICATION_JSON));

        var credentials = resolver.resolve("job-1", REPO).orElseThrow();

        assertThat(credentials.username()).isEqualTo("bot");
        assertThat(credentials.token()).isEqualTo("t0k");
        assertThat(credentials.toString()).doesNotContain("t0k");
        server.verify();
    }

    @Test
    void anUnkeyedWorkspaceNeverAsksForCredentials() {
        assertThat(resolver.resolve(null, REPO)).isEmpty();
        assertThat(resolver.resolve(" ", REPO)).isEmpty();
        server.verify();
    }

    @Test
    void anInactiveOrForeignWorkspaceFailsClosed() {
        expectServiceToken();
        server.expect(requestTo("http://ai-control:8080/internal/git-workspaces/job-9/credentials?repositoryUrl=https%3A%2F%2Fgithub.com%2Forg%2Frepo.git"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> resolver.resolve("job-9", REPO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no active workspace job-9");
    }

    @Test
    void keyedWorkspacesFailClosedWhenNotConfigured() {
        var unconfigured = new AiControlGitCredentialResolver(new GitToolsProperties.Credentials());

        assertThatThrownBy(() -> unconfigured.resolve("job-1", REPO)).isInstanceOf(IllegalStateException.class);
    }
}
