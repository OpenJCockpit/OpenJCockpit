package nl.metafactory.agents.config;

import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.security.SpecQueueRunnerTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defence-in-depth matrix of embabel-agent-service's {@link SecurityConfig}: human tokens with each realm role,
 * the spec-queue runner, ai-control's service identity and another service client, against a catch-all probe.
 */
@SpringJUnitWebConfig(AuthorizationMatrixTest.TestApp.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks",
        "openjcockpit.security.jwt-issuer=http://issuer.example"
})
class AuthorizationMatrixTest {

    private static final String RUNNER_ISSUER = "urn:openjcockpit:ai-control-service:spec-queue-runner";
    private static final String AI_CONTROL_CLIENT = "openjcockpit-ai-control";

    private static final String NO_ROLE = "human-no-role";
    private static final String VIEWER = "viewer";
    private static final String OPERATOR = "operator";
    private static final String APPROVER = "approver";
    private static final String ADMIN = "admin";
    private static final String RUNNER = "spec-queue-runner";
    private static final String AI_CONTROL = "ai-control-service";
    private static final String OTHER_SERVICE = "other-service-client";

    private static final List<String> PERSONAS =
            List.of(NO_ROLE, VIEWER, OPERATOR, APPROVER, ADMIN, RUNNER, AI_CONTROL, OTHER_SERVICE);

    /** Any authenticated caller except the runner. */
    private static final Set<String> HUMAN =
            Set.of(NO_ROLE, VIEWER, OPERATOR, APPROVER, ADMIN, AI_CONTROL, OTHER_SERVICE);
    private static final Set<String> ANY = Set.copyOf(PERSONAS);
    private static final Set<String> START = Set.of(RUNNER, AI_CONTROL, OPERATOR, ADMIN);
    private static final Set<String> STOP = Set.of(AI_CONTROL, OPERATOR, ADMIN);
    private static final Set<String> APPROVERS = Set.of(APPROVER, ADMIN);
    private static final Set<String> OPERATORS = Set.of(OPERATOR, ADMIN);
    private static final Set<String> ADMINS = Set.of(ADMIN);

    record Endpoint(HttpMethod method, String path, Set<String> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static final List<Endpoint> ENDPOINTS = List.of(
            // reads (re-checked by role in ai-control; here any authenticated non-runner caller)
            e(GET, "/api/workflows", HUMAN),
            e(GET, "/api/workflows/export", HUMAN),
            e(GET, "/api/workflows/w/decision-logs", HUMAN),
            e(GET, "/api/workflows/w/executions", HUMAN),
            e(GET, "/api/workflows/w/executions/r", HUMAN),
            e(GET, "/api/workflow-executions/x/decision-logs", HUMAN),
            e(GET, "/api/workflow-groups", HUMAN),
            e(GET, "/api/agent-runs/r/outputs", HUMAN),
            e(GET, "/api/agent-runs/r/approval-gate", HUMAN),
            e(GET, "/api/agent-runs/r/approval-decisions", HUMAN),
            e(GET, "/api/agent-definitions", HUMAN),
            e(GET, "/api/agent-definitions/a", HUMAN),
            e(GET, "/api/skill-definitions", HUMAN),
            e(GET, "/api/subagent-definitions/s", HUMAN),
            e(GET, "/api/agent-catalog", HUMAN),
            e(GET, "/api/opa-config", HUMAN),
            e(GET, "/api/opa-config/health", HUMAN),
            e(GET, "/api/a2a-services", HUMAN),
            // service-to-service: controllers check azp
            e(POST, "/internal/git-workspace-tickets", HUMAN),
            e(GET, "/internal/agent-runtime/v1/workflow-runs/r", HUMAN),
            // the runner's read routes
            e(GET, "/api/workflows/w", ANY),
            e(GET, "/api/workflow-groups/g", ANY),
            e(GET, "/api/agent-runs/r", ANY),
            // run lifecycle
            e(POST, "/api/workflows/w/start", START),
            e(DELETE, "/api/agent-runs/r", STOP),
            // approval decisions
            e(POST, "/api/agent-runs/r/approval-gate/decision", APPROVERS),
            // workflow, group and definition mutations
            e(POST, "/api/workflows", OPERATORS),
            e(POST, "/api/workflows/import", OPERATORS),
            e(PUT, "/api/workflows/w", OPERATORS),
            e(DELETE, "/api/workflows/w", OPERATORS),
            e(POST, "/api/workflow-groups", OPERATORS),
            e(PUT, "/api/workflow-groups/g", OPERATORS),
            e(DELETE, "/api/workflow-groups/g", OPERATORS),
            e(POST, "/api/agent-definitions", OPERATORS),
            e(PUT, "/api/agent-definitions/a", OPERATORS),
            e(DELETE, "/api/agent-definitions/a", OPERATORS),
            e(POST, "/api/agent-definitions/generate-from-prompt", OPERATORS),
            e(POST, "/api/skill-definitions", OPERATORS),
            e(PUT, "/api/skill-definitions/s", OPERATORS),
            e(DELETE, "/api/skill-definitions/s", OPERATORS),
            e(POST, "/api/skill-definitions/generate-from-prompt", OPERATORS),
            e(POST, "/api/subagent-definitions", OPERATORS),
            e(PUT, "/api/subagent-definitions/s", OPERATORS),
            e(DELETE, "/api/subagent-definitions/s", OPERATORS),
            e(POST, "/api/subagent-definitions/generate-from-prompt", OPERATORS),
            // platform configuration
            e(PUT, "/api/opa-config", ADMINS),
            e(POST, "/api/a2a-services", ADMINS),
            e(PUT, "/api/a2a-services/x", ADMINS),
            e(DELETE, "/api/a2a-services/x", ADMINS));

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestApp {
        @Bean
        SpecQueueRunnerTokenProperties specQueueRunnerTokenProperties() {
            return runnerProperties();
        }

        @Bean
        GitWorkspaceProperties gitWorkspaceProperties() {
            return new GitWorkspaceProperties();
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @RestController
    static class Probe {
        @RequestMapping("/**")
        ResponseEntity<String> any() {
            return ResponseEntity.ok("reached");
        }
    }

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static Endpoint e(HttpMethod method, String path, Set<String> allowed) {
        return new Endpoint(method, path, allowed);
    }

    private static SpecQueueRunnerTokenProperties runnerProperties() {
        var props = new SpecQueueRunnerTokenProperties();
        props.setSecret("test-only-spec-queue-runner-secret-32-bytes-minimum");
        return props;
    }

    /** A token for the persona, converted by the production converter. */
    static RequestPostProcessor token(String persona) {
        Consumer<Jwt.Builder> claims = switch (persona) {
            case RUNNER -> j -> j.issuer(RUNNER_ISSUER).subject("spec-queue-runner");
            case AI_CONTROL -> j -> j.claim("azp", AI_CONTROL_CLIENT).subject("service-account-ai-control")
                    .claim("realm_access", Map.of("roles", List.of("default-roles-openjcockpit")));
            case OTHER_SERVICE -> j -> j.claim("azp", "openjcockpit-agent-runtime").subject("service-account-x");
            case NO_ROLE -> j -> j.claim("azp", "openjcockpit").subject("someone")
                    .claim("realm_access", Map.of("roles", List.of("default-roles-openjcockpit", "offline_access")));
            default -> j -> j.claim("azp", "openjcockpit").subject(persona)
                    .claim("realm_access", Map.of("roles", List.of("openjcockpit-" + persona)));
        };
        var converter = SecurityConfig.jwtAuthenticationConverter(runnerProperties(), AI_CONTROL_CLIENT);
        return jwt().jwt(j -> claims.accept(j.issuer("http://issuer.example")))
                .authorities(j -> converter.convert(j).getAuthorities());
    }

    static Stream<Arguments> endpointsByPersona() {
        List<Arguments> out = new ArrayList<>();
        for (Endpoint endpoint : ENDPOINTS) {
            for (String persona : PERSONAS) {
                out.add(Arguments.of(endpoint, persona));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("endpointsByPersona")
    void roleMatrix(Endpoint endpoint, String persona) throws Exception {
        int expected = endpoint.allowed().contains(persona) ? 200 : 403;

        int actual = mockMvc.perform(request(endpoint.method(), endpoint.path()).with(token(persona))
                        .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(actual).as("%s as %s", endpoint, persona).isEqualTo(expected);
    }

    @ParameterizedTest(name = "unauthenticated: {0}")
    @MethodSource("endpoints")
    void unauthenticatedRequestsAreRejectedWith401(Endpoint endpoint) throws Exception {
        mockMvc.perform(request(endpoint.method(), endpoint.path())).andExpect(status().isUnauthorized());
    }

    static Stream<Endpoint> endpoints() {
        return ENDPOINTS.stream();
    }

    @Test
    void forbiddenResponseCarriesAnApiErrorBody() throws Exception {
        mockMvc.perform(request(POST, "/api/agent-runs/r/approval-gate/decision").with(token(OPERATOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void aiControlIdentityIsRecognisedOnlyByItsAzp() {
        var converter = SecurityConfig.jwtAuthenticationConverter(runnerProperties(), AI_CONTROL_CLIENT);
        Jwt other = Jwt.withTokenValue("t").header("alg", "none").subject("s").claim("azp", "openjcockpit")
                .claim("scope", "AI_CONTROL_SERVICE").build();

        assertThat(converter.convert(other).getAuthorities()).extracting(a -> a.getAuthority())
                .doesNotContain(SecurityConfig.AI_CONTROL_SERVICE)
                .contains("SCOPE_AI_CONTROL_SERVICE");
    }
}
