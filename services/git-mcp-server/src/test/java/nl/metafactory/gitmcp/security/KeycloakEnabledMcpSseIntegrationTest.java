package nl.metafactory.gitmcp.security;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The paired MCP client (MCP Java SDK, SSE transport, as used by embabel-agent-service) against the secured
 * server over real HTTP: with a valid bearer token on every request the SSE handshake and the message
 * requests succeed and the frozen tool list comes back; without one the client cannot connect. Runs with a
 * separate management port, as in production, to check what is anonymous there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.banner-mode=off")
class KeycloakEnabledMcpSseIntegrationTest {

    private static final TestKeycloak KEYCLOAK = new TestKeycloak();
    private static final int MANAGEMENT_PORT = freePort();

    private static int freePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("openjcockpit.git-mcp.security.keycloak-enabled", () -> "true");
        registry.add("openjcockpit.git-mcp.security.issuer-uri", KEYCLOAK::issuer);
        registry.add("openjcockpit.git-mcp.security.jwk-set-uri", KEYCLOAK::jwkSetUri);
        registry.add("openjcockpit.git-mcp.security.expected-audience", () -> TestKeycloak.AUDIENCE);
        registry.add("openjcockpit.git-mcp.security.allowed-azp", () -> TestKeycloak.LITELLM_CLIENT);
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @AfterAll
    static void stopKeycloak() {
        KEYCLOAK.close();
    }

    @LocalServerPort
    private int port;

    private McpSyncClient client(String authorization, List<String> requestLog) {
        return client(authorization, requestLog, Duration.ofSeconds(10));
    }

    private McpSyncClient client(String authorization, List<String> requestLog, Duration timeout) {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .httpRequestCustomizer((builder, method, uri, body, context) -> {
                    requestLog.add(method + " " + uri.getPath());
                    if (authorization != null) {
                        builder.header("Authorization", authorization);
                    }
                })
                .build();
        return McpClient.sync(transport).requestTimeout(timeout).initializationTimeout(timeout).build();
    }

    @Test
    void authenticatedClientListsAndCallsToolsOverSse() {
        var requests = new ArrayList<String>();
        var client = client("Bearer " + KEYCLOAK.validToken(), requests);
        try {
            client.initialize();
            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).contains("git_checkout_branch", "git_commit", "git_push");

            // A tool call reaches the tool (it fails on the bogus repository, not on authentication).
            var result = client.callTool(new McpSchema.CallToolRequest("git_list_files",
                    java.util.Map.of("repositoryUrl", "https://git.invalid/never/cloned.git")));
            assertThat(result).isNotNull();
        } finally {
            client.closeGracefully();
        }
        // The token went on the SSE handshake and on every message request.
        assertThat(requests).contains("GET /sse").anyMatch(request -> request.startsWith("POST /mcp/message"));
    }

    @Test
    void unauthenticatedClientCannotConnect() {
        var client = client(null, new ArrayList<>(), Duration.ofSeconds(2));
        try {
            assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
        } finally {
            client.close();
        }
    }

    @Test
    void clientOfAnotherServiceCannotConnect() {
        var client = client("Bearer " + KEYCLOAK.token(claims -> claims.claim("azp", "openjcockpit-agent-service")),
                new ArrayList<>(), Duration.ofSeconds(2));
        try {
            assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
        } finally {
            client.close();
        }
    }

    private static int status(String url) throws Exception {
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            return http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @Test
    void onTheManagementPortOnlyProbesAndPrometheusAreAnonymous() throws Exception {
        String management = "http://localhost:" + MANAGEMENT_PORT;

        assertThat(status(management + "/actuator/health/readiness")).isEqualTo(200);
        assertThat(status(management + "/actuator/health/liveness")).isEqualTo(200);
        assertThat(status(management + "/actuator/prometheus")).isEqualTo(200);
        assertThat(status(management + "/actuator/info")).isEqualTo(401);
        // On the API port Prometheus still needs a token.
        assertThat(status("http://localhost:" + port + "/actuator/prometheus")).isEqualTo(401);
        assertThat(status("http://localhost:" + port + "/sse")).isEqualTo(401);
    }
}
