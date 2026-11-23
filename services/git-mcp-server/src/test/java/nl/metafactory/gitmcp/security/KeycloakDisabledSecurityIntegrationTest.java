package nl.metafactory.gitmcp.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KEYCLOAK_ENABLED unset (default false) and no Keycloak settings at all: the context starts, there is no JWT
 * machinery, and requests with no, malformed or arbitrary Authorization headers all reach the MCP transport.
 * The MCP tool round trip over SSE in this mode is covered by McpToolsListSseIntegrationTest.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
class KeycloakDisabledSecurityIntegrationTest {

    private static final String MESSAGE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private List<SecurityFilterChain> chains;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void noJwtDecoderAndNoBearerTokenFilterAreActive() {
        assertThat(context.getBeanNamesForType(JwtDecoder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(KeycloakMcpSecurityConfig.class)).isEmpty();
        assertThat(chains).hasSize(1);
        List<Filter> filters = chains.getFirst().getFilters();
        assertThat(filters).noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
    }

    @Test
    void messageEndpointIsReachableWithAnyOrNoAuthorizationHeader() throws Exception {
        for (String authorization : new String[] {null, "Bearer not-a-jwt", "Bearer", "Basic dXNlcjpwYXNz", "garbage"}) {
            var request = post("/mcp/message").queryParam("sessionId", "unknown-session")
                    .contentType(MediaType.APPLICATION_JSON).content(MESSAGE);
            if (authorization != null) {
                request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            // The MCP transport itself answers: unknown session -> 404 (never 401/403).
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).as(String.valueOf(authorization))
                    .isEqualTo(404);
        }
    }

    @Test
    void sseStreamOpensWithoutAToken() throws Exception {
        mvc.perform(get("/sse").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted());
        mvc.perform(get("/sse").accept(MediaType.TEXT_EVENT_STREAM).header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z"))
                .andExpect(request().asyncStarted());
    }

    @Test
    void actuatorStaysAsBefore() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
