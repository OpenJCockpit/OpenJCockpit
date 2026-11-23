package nl.metafactory.gitmcp.security;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KEYCLOAK_ENABLED=true through the real SecurityFilterChain and the real decoder, with signed tokens and a
 * JWKS served by {@link TestKeycloak}. Both MCP routes of the SSE transport (GET /sse, POST /mcp/message) are
 * exercised, plus the anonymous health probes.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
class KeycloakEnabledSecurityIntegrationTest {

    private static final TestKeycloak KEYCLOAK = new TestKeycloak();
    private static final String MESSAGE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("openjcockpit.git-mcp.security.keycloak-enabled", () -> "true");
        registry.add("openjcockpit.git-mcp.security.issuer-uri", KEYCLOAK::issuer);
        registry.add("openjcockpit.git-mcp.security.jwk-set-uri", KEYCLOAK::jwkSetUri);
        registry.add("openjcockpit.git-mcp.security.expected-audience", () -> TestKeycloak.AUDIENCE);
        registry.add("openjcockpit.git-mcp.security.allowed-azp",
                () -> TestKeycloak.LITELLM_CLIENT + "," + TestKeycloak.AGENT_SERVICE_CLIENT);
    }

    @AfterAll
    static void stopKeycloak() {
        KEYCLOAK.close();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions message(String authorization) throws Exception {
        var request = post("/mcp/message").queryParam("sessionId", "unknown-session")
                .contentType(MediaType.APPLICATION_JSON).content(MESSAGE);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request);
    }

    private ResultActions messageWithToken(Consumer<JWTClaimsSet.Builder> claims) throws Exception {
        return message("Bearer " + KEYCLOAK.token(claims));
    }

    @Test
    void usesTheKeycloakDecoder() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    void missingTokenIs401OnBothMcpRoutes() throws Exception {
        message(null).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
        mvc.perform(get("/sse").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        mvc.perform(get("/anything-else")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedUnsignedAndForeignSignedTokensAre401() throws Exception {
        message("Bearer not-a-jwt").andExpect(status().isUnauthorized());
        message("Bearer").andExpect(status().isUnauthorized());
        message("Basic dXNlcjpwYXNz").andExpect(status().isUnauthorized());
        message("Bearer " + KEYCLOAK.unsignedToken()).andExpect(status().isUnauthorized());
        message("Bearer " + KEYCLOAK.tokenSignedWithOtherKey()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndNotYetValidTokensAre401() throws Exception {
        Instant now = Instant.now();
        messageWithToken(claims -> claims.issueTime(Date.from(now.minusSeconds(900)))
                .notBeforeTime(Date.from(now.minusSeconds(900))).expirationTime(Date.from(now.minusSeconds(600))))
                .andExpect(status().isUnauthorized());
        messageWithToken(claims -> claims.notBeforeTime(Date.from(now.plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerAudienceOrClientIs401() throws Exception {
        messageWithToken(claims -> claims.issuer("https://evil.example.test/realms/test"))
                .andExpect(status().isUnauthorized());
        messageWithToken(claims -> claims.audience((String) null)).andExpect(status().isUnauthorized());
        messageWithToken(claims -> claims.audience(List.of("account"))).andExpect(status().isUnauthorized());
        messageWithToken(claims -> claims.claim("azp", null)).andExpect(status().isUnauthorized());
        messageWithToken(claims -> claims.claim("azp", "openjcockpit-dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
    }

    @Test
    void validTokenReachesTheMcpMessageEndpoint() throws Exception {
        // Past security the MCP transport answers for the session itself: unknown session -> 404, not 401/403.
        int status = messageWithToken(claims -> { }).andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
        assertThat(status).isEqualTo(404);
    }

    @Test
    void multipleAudiencesAndEveryAllowedClientAreAccepted() throws Exception {
        assertThat(messageWithToken(claims -> claims.audience(List.of("account", TestKeycloak.AUDIENCE)))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(messageWithToken(claims -> claims.claim("azp", TestKeycloak.AGENT_SERVICE_CLIENT))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void validTokenOpensTheSseStream() throws Exception {
        mvc.perform(get("/sse").accept(MediaType.TEXT_EVENT_STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + KEYCLOAK.validToken()))
                .andExpect(request().asyncStarted());
    }

    @Test
    void protectedResourceMetadataIsNotServedAnonymously() throws Exception {
        mvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isUnauthorized());
        mvc.perform(get("/.well-known/oauth-protected-resource/sse")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + KEYCLOAK.validToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesAreAnonymousButOtherActuatorEndpointsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        // No separate management port in this test: Prometheus needs a token like everything else.
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
