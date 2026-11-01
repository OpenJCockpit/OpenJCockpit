package nl.metafactory.aicontrol.config;

import nl.metafactory.common.security.KeycloakRealmRoleConverter;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The endpoint → role matrix of the dashboard API, checked against ai-control's real {@link SecurityConfig}
 * with a catch-all probe standing in for the controllers: every endpoint × every persona, plus requests that
 * match no rule. {@code SecurityConfigTest} proves that {@link #ENDPOINTS} lists every real handler mapping.
 */
@SpringJUnitWebConfig(AuthorizationMatrixTest.TestApp.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks",
        "openjcockpit.security.jwt-issuer=http://issuer.example"
})
class AuthorizationMatrixTest {

    static final String ADMIN = "openjcockpit-admin";
    static final String OPERATOR = "openjcockpit-operator";
    static final String APPROVER = "openjcockpit-approver";
    static final String VIEWER = "openjcockpit-viewer";
    /** Any authenticated caller; the controller itself authorises by token {@code azp}. */
    static final String AUTHENTICATED = "authenticated";

    record Endpoint(HttpMethod method, String template, String requires) {
        String path() {
            return template.replaceAll("\\{[^}]+}", "x1");
        }

        @Override
        public String toString() {
            return method + " " + template + " -> " + requires;
        }
    }

    /** Persona name → realm roles in its token. */
    static final Map<String, List<String>> PERSONAS = Map.of(
            "no-role", List.of(),
            "viewer", List.of(VIEWER),
            "operator", List.of(OPERATOR),
            "approver", List.of(APPROVER),
            "operator+approver", List.of(OPERATOR, APPROVER),
            "admin", List.of(ADMIN));

    /** What each role is allowed to act as (written out independently of the production role hierarchy). */
    private static final Map<String, Set<String>> GRANTS = Map.of(
            ADMIN, Set.of(ADMIN, OPERATOR, APPROVER, VIEWER),
            OPERATOR, Set.of(OPERATOR, VIEWER),
            APPROVER, Set.of(APPROVER, VIEWER),
            VIEWER, Set.of(VIEWER));

    /** Every route ai-control-service serves, with the least role required. */
    static final List<Endpoint> ENDPOINTS = List.of(
            // service-to-service
            e(GET, "/internal/git-workspaces/{workspaceKey}/credentials", AUTHENTICATED),
            // workspace / model routing
            e(GET, "/api/workspaces/{customerId}", VIEWER),
            e(POST, "/api/model-routing/decision", VIEWER),
            e(GET, "/api/specs/{customerId}", VIEWER),
            // projects
            e(GET, "/api/projects", VIEWER),
            e(GET, "/api/projects/all", VIEWER),
            e(POST, "/api/projects", ADMIN),
            e(PUT, "/api/projects/{id}", ADMIN),
            e(PATCH, "/api/projects/{id}/activate", ADMIN),
            e(PATCH, "/api/projects/{id}/deactivate", ADMIN),
            e(PATCH, "/api/projects/{id}/new-project", ADMIN),
            e(GET, "/api/projects/{id}/hermes-config", VIEWER),
            e(POST, "/api/projects/{id}/hermes-config", ADMIN),
            e(DELETE, "/api/projects/{id}/hermes-config", ADMIN),
            e(GET, "/api/projects/{id}/jira-config", VIEWER),
            e(POST, "/api/projects/{id}/jira-config", ADMIN),
            e(DELETE, "/api/projects/{id}/jira-config", ADMIN),
            e(GET, "/api/projects/{id}/document-folder-config", VIEWER),
            e(POST, "/api/projects/{id}/document-folder-config", ADMIN),
            e(DELETE, "/api/projects/{id}/document-folder-config", ADMIN),
            e(POST, "/api/projects/{id}/git-check", OPERATOR),
            e(GET, "/api/projects/{id}/git-status", VIEWER),
            e(GET, "/api/projects/{id}/git-credentials", ADMIN),
            e(POST, "/api/projects/{id}/git-credentials", ADMIN),
            e(DELETE, "/api/projects/{id}/git-credentials", ADMIN),
            // specs and spec queue
            e(GET, "/api/projects/{projectId}/spec-files", VIEWER),
            e(PUT, "/api/projects/{projectId}/spec-files", OPERATOR),
            e(GET, "/api/projects/{projectId}/spec-init/status", VIEWER),
            e(POST, "/api/projects/{projectId}/spec-init", OPERATOR),
            e(GET, "/api/projects/{projectId}/spec-queue", VIEWER),
            e(POST, "/api/projects/{projectId}/spec-queue/items", OPERATOR),
            e(PATCH, "/api/projects/{projectId}/spec-queue/items/{itemId}", OPERATOR),
            e(DELETE, "/api/projects/{projectId}/spec-queue/items/{itemId}", OPERATOR),
            e(POST, "/api/projects/{projectId}/spec-queue/items/{itemId}/retry", OPERATOR),
            e(POST, "/api/projects/{projectId}/spec-queue/items/{itemId}/skip", OPERATOR),
            e(PUT, "/api/projects/{projectId}/spec-queue/order", OPERATOR),
            e(POST, "/api/projects/{projectId}/spec-queue/pause", OPERATOR),
            e(POST, "/api/projects/{projectId}/spec-queue/resume", OPERATOR),
            e(GET, "/api/projects/{projectId}/spec-queue/settings", VIEWER),
            e(PUT, "/api/projects/{projectId}/spec-queue/settings", ADMIN),
            // runs and approval gates
            e(GET, "/api/agent-runs/{runId}", VIEWER),
            e(DELETE, "/api/agent-runs/{runId}", OPERATOR),
            e(GET, "/api/agent-runs/{runId}/approval-gate", VIEWER),
            e(POST, "/api/agent-runs/{runId}/approval-gate/decision", APPROVER),
            e(GET, "/api/agent-runs/{runId}/approval-decisions", VIEWER),
            // catalogs and definitions
            e(GET, "/api/agent-catalog", VIEWER),
            e(GET, "/api/agent-catalog/providers/{provider}/agents/{name}/card", VIEWER),
            e(GET, "/api/agent-definitions", VIEWER),
            e(POST, "/api/agent-definitions", OPERATOR),
            e(GET, "/api/agent-definitions/{name}", VIEWER),
            e(PUT, "/api/agent-definitions/{name}", OPERATOR),
            e(DELETE, "/api/agent-definitions/{name}", OPERATOR),
            e(POST, "/api/agent-definitions/generate-from-prompt", OPERATOR),
            e(GET, "/api/skill-definitions", VIEWER),
            e(POST, "/api/skill-definitions", OPERATOR),
            e(GET, "/api/skill-definitions/{name}", VIEWER),
            e(PUT, "/api/skill-definitions/{name}", OPERATOR),
            e(DELETE, "/api/skill-definitions/{name}", OPERATOR),
            e(POST, "/api/skill-definitions/generate-from-prompt", OPERATOR),
            e(GET, "/api/subagent-definitions", VIEWER),
            e(POST, "/api/subagent-definitions", OPERATOR),
            e(GET, "/api/subagent-definitions/{name}", VIEWER),
            e(PUT, "/api/subagent-definitions/{name}", OPERATOR),
            e(DELETE, "/api/subagent-definitions/{name}", OPERATOR),
            e(POST, "/api/subagent-definitions/generate-from-prompt", OPERATOR),
            // workflow runs
            e(POST, "/api/agentic-workflows/start", OPERATOR),
            e(POST, "/api/agentic-workflows/preflight", OPERATOR),
            e(GET, "/api/git-workspace-jobs/{jobId}", VIEWER),
            e(GET, "/api/git-workspace-jobs/{jobId}/events", VIEWER),
            // workflows and groups
            e(GET, "/api/workflows", VIEWER),
            e(POST, "/api/workflows", OPERATOR),
            e(GET, "/api/workflows/export", VIEWER),
            e(POST, "/api/workflows/import", OPERATOR),
            e(GET, "/api/workflows/{id}", VIEWER),
            e(PUT, "/api/workflows/{id}", OPERATOR),
            e(DELETE, "/api/workflows/{id}", OPERATOR),
            e(POST, "/api/workflows/{id}/start", OPERATOR),
            e(GET, "/api/workflows/{id}/decision-logs", VIEWER),
            e(GET, "/api/workflow-executions/{id}/decision-logs", VIEWER),
            e(GET, "/api/workflows/{id}/executions", VIEWER),
            e(GET, "/api/workflows/{id}/executions/{runId}", VIEWER),
            e(GET, "/api/workflow-groups", VIEWER),
            e(POST, "/api/workflow-groups", OPERATOR),
            e(GET, "/api/workflow-groups/{id}", VIEWER),
            e(PUT, "/api/workflow-groups/{id}", OPERATOR),
            e(DELETE, "/api/workflow-groups/{id}", OPERATOR),
            // platform configuration
            e(GET, "/api/opa-config", VIEWER),
            e(PUT, "/api/opa-config", ADMIN),
            e(GET, "/api/opa-config/health", VIEWER),
            e(GET, "/api/skills-marketplaces", VIEWER),
            e(POST, "/api/skills-marketplaces", ADMIN),
            e(PUT, "/api/skills-marketplaces/{id}", ADMIN),
            e(DELETE, "/api/skills-marketplaces/{id}", ADMIN),
            e(GET, "/api/skill-catalog", VIEWER),
            e(GET, "/api/a2a-services", VIEWER),
            e(POST, "/api/a2a-services", ADMIN),
            e(PUT, "/api/a2a-services/{id}", ADMIN),
            e(DELETE, "/api/a2a-services/{id}", ADMIN));

    /** Requests no rule maps: denied for everyone, admin included. */
    static final List<Endpoint> UNMAPPED = List.of(
            e(GET, "/api/not-a-route", null),
            e(GET, "/api/projects/{id}/not-a-route", null),
            e(POST, "/api/projects/{id}/not-a-route", null),
            e(PUT, "/api/agent-runs/{runId}", null),
            e(PATCH, "/api/workflows/{id}", null),
            e(POST, "/api/agent-runs/{runId}/approval-gate", null),
            e(GET, "/internal/not-a-route", null),
            e(GET, "/not-api", null));

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestApp {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", new CorsConfiguration());
            return source;
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    /** Reached only when authorisation let the request through. */
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

    private static Endpoint e(HttpMethod method, String template, String requires) {
        return new Endpoint(method, template, requires);
    }

    /** A token as Keycloak would issue it, converted by the production realm-role converter. */
    static RequestPostProcessor token(List<String> realmRoles) {
        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
        return jwt().jwt(j -> j.subject("user").claim("scope", "openid")
                        .claim("realm_access", Map.of("roles", realmRoles)))
                .authorities(j -> converter.convert(j).getAuthorities());
    }

    static boolean allowed(List<String> realmRoles, String requires) {
        if (requires == null) {
            return false;
        }
        if (AUTHENTICATED.equals(requires)) {
            return true;
        }
        return realmRoles.stream().anyMatch(role -> GRANTS.get(role).contains(requires));
    }

    private MockHttpServletRequestBuilder call(Endpoint endpoint) {
        return request(endpoint.method(), endpoint.path()).contentType("application/json").content("{}");
    }

    static Stream<Arguments> endpointsByPersona() {
        List<Arguments> out = new ArrayList<>();
        for (Endpoint endpoint : ENDPOINTS) {
            PERSONAS.forEach((persona, roles) -> out.add(Arguments.of(endpoint, persona)));
        }
        for (Endpoint endpoint : UNMAPPED) {
            PERSONAS.forEach((persona, roles) -> out.add(Arguments.of(endpoint, persona)));
        }
        return out.stream();
    }

    static Stream<Endpoint> allEndpoints() {
        return Stream.concat(ENDPOINTS.stream(), UNMAPPED.stream());
    }

    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("endpointsByPersona")
    void roleMatrix(Endpoint endpoint, String persona) throws Exception {
        List<String> roles = PERSONAS.get(persona);
        int expected = allowed(roles, endpoint.requires()) ? 200 : 403;

        int actual = mockMvc.perform(call(endpoint).with(token(roles))).andReturn().getResponse().getStatus();

        assertThat(actual).as("%s as %s", endpoint, persona).isEqualTo(expected);
    }

    @ParameterizedTest(name = "unauthenticated: {0}")
    @MethodSource("allEndpoints")
    void unauthenticatedRequestsAreRejectedWith401(Endpoint endpoint) throws Exception {
        mockMvc.perform(call(endpoint)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndInfoArePublicButOtherActuatorEndpointsNeedAdmin() throws Exception {
        mockMvc.perform(request(GET, "/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(request(GET, "/actuator/info")).andExpect(status().isOk());
        mockMvc.perform(request(GET, "/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(request(GET, "/actuator/metrics").with(token(List.of(OPERATOR, APPROVER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(request(POST, "/actuator/loggers/root").with(token(List.of(ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void forbiddenResponseCarriesAnApiErrorBodyTheDashboardCanShow() throws Exception {
        mockMvc.perform(request(POST, "/api/projects").with(token(List.of(OPERATOR))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void operatorRunsWorkflowsButCannotApproveOrAdministerProjects() throws Exception {
        var operator = token(List.of(OPERATOR));
        mockMvc.perform(request(POST, "/api/workflows/wf/start").with(operator)).andExpect(status().isOk());
        mockMvc.perform(request(POST, "/api/agent-runs/r/approval-gate/decision").with(operator))
                .andExpect(status().isForbidden());
        mockMvc.perform(request(POST, "/api/projects").with(operator)).andExpect(status().isForbidden());
        mockMvc.perform(request(POST, "/api/projects/p/git-credentials").with(operator))
                .andExpect(status().isForbidden());
    }

    @Test
    void approverDecidesGatesButCannotStartWorkflowsOrAdministerProjects() throws Exception {
        var approver = token(List.of(APPROVER));
        mockMvc.perform(request(POST, "/api/agent-runs/r/approval-gate/decision").with(approver))
                .andExpect(status().isOk());
        mockMvc.perform(request(POST, "/api/workflows/wf/start").with(approver)).andExpect(status().isForbidden());
        mockMvc.perform(request(POST, "/api/agentic-workflows/start").with(approver))
                .andExpect(status().isForbidden());
        mockMvc.perform(request(PUT, "/api/projects/p").with(approver)).andExpect(status().isForbidden());
    }

    @Test
    void viewerIsReadOnly() throws Exception {
        var viewer = token(List.of(VIEWER));
        mockMvc.perform(request(GET, "/api/projects").with(viewer)).andExpect(status().isOk());
        mockMvc.perform(request(DELETE, "/api/workflows/wf").with(viewer)).andExpect(status().isForbidden());
        mockMvc.perform(request(GET, "/api/projects/p/git-credentials").with(viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRealmRolesAndScopesGrantNothing() throws Exception {
        var other = token(List.of("offline_access", "uma_authorization", "default-roles-openjcockpit",
                "OPENJCOCKPIT-ADMIN", "admin"));
        mockMvc.perform(request(GET, "/api/projects").with(other)).andExpect(status().isForbidden());
    }

    @Test
    void matrixCoversEveryPersonaAndEveryRoleCategory() {
        assertThat(ENDPOINTS).extracting(Endpoint::requires)
                .contains(ADMIN, OPERATOR, APPROVER, VIEWER, AUTHENTICATED);
        assertThat(ENDPOINTS).doesNotHaveDuplicates();
    }
}
