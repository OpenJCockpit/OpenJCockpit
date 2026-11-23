package nl.metafactory.gitmcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keycloak unreachable: the server still starts (keys are fetched lazily), and a correctly signed token is
 * refused (HTTP 500) because its signature cannot be verified. There is no fallback to anonymous access.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.banner-mode=off")
class KeycloakUnavailableSecurityIntegrationTest {

    /** Started only to mint a token and learn free URLs, then stopped before the server needs its keys. */
    private static final TestKeycloak KEYCLOAK = new TestKeycloak();
    private static final String TOKEN = KEYCLOAK.validToken();

    static {
        KEYCLOAK.close();
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("openjcockpit.git-mcp.security.keycloak-enabled", () -> "true");
        registry.add("openjcockpit.git-mcp.security.issuer-uri", KEYCLOAK::issuer);
        registry.add("openjcockpit.git-mcp.security.jwk-set-uri", KEYCLOAK::jwkSetUri);
        registry.add("openjcockpit.git-mcp.security.expected-audience", () -> TestKeycloak.AUDIENCE);
        registry.add("openjcockpit.git-mcp.security.allowed-azp", () -> TestKeycloak.LITELLM_CLIENT);
    }

    @LocalServerPort
    private int port;

    @Test
    void requestsAreRefusedWhileTheSigningKeysCannotBeFetched() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp/message?sessionId=x"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .build();

        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());

            // Refused as a server-side failure (Spring Security's AuthenticationServiceException), never let through.
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).doesNotContain("Exception");
            var health = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
        }
    }
}
