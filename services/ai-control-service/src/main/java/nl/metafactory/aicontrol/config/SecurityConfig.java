package nl.metafactory.aicontrol.config;

import nl.metafactory.common.security.KeycloakRealmRoleConverter;
import nl.metafactory.common.security.ResourceServerSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import static nl.metafactory.common.security.OpenJCockpitRoles.ADMIN;
import static nl.metafactory.common.security.OpenJCockpitRoles.APPROVER;
import static nl.metafactory.common.security.OpenJCockpitRoles.OPERATOR;
import static nl.metafactory.common.security.OpenJCockpitRoles.VIEWER;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

/**
 * Role-based, deny-by-default authorisation of the dashboard API. Every route is mapped explicitly to
 * the least role that may call it ({@code OpenJCockpitRoles}; the role hierarchy makes admin imply
 * every role and operator/approver imply viewer). Anything not mapped, including a newly added endpoint,
 * is denied until it is added here. See docs/readme/governance/authorization-model.md.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ResourceServerSecurityConfig.class)
public class SecurityConfig {

    private static final String[] DEFINITION_COLLECTIONS = {
            "/api/agent-definitions", "/api/skill-definitions", "/api/subagent-definitions"};

    /** Every read (GET) endpoint open to viewers; git credentials are admin-only and not listed here. */
    private static final String[] VIEWER_READS = {
            "/api/workspaces/*", "/api/specs/*",
            "/api/projects", "/api/projects/all", "/api/projects/*/git-status",
            "/api/projects/*/hermes-config", "/api/projects/*/jira-config", "/api/projects/*/document-folder-config",
            "/api/projects/*/spec-files", "/api/projects/*/spec-init/status",
            "/api/projects/*/spec-queue", "/api/projects/*/spec-queue/settings",
            "/api/agent-runs/*", "/api/agent-runs/*/approval-gate", "/api/agent-runs/*/approval-decisions",
            "/api/agent-catalog", "/api/agent-catalog/providers/*/agents/*/card",
            "/api/git-workspace-jobs/*", "/api/git-workspace-jobs/*/events",
            "/api/workflows", "/api/workflows/export", "/api/workflows/*", "/api/workflows/*/decision-logs",
            "/api/workflows/*/executions", "/api/workflows/*/executions/*",
            "/api/workflow-executions/*/decision-logs",
            "/api/workflow-groups", "/api/workflow-groups/*",
            "/api/opa-config", "/api/opa-config/health",
            "/api/skills-marketplaces", "/api/skill-catalog", "/api/a2a-services"};

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        return ResourceServerSecurityConfig.buildApiChain(http, new KeycloakRealmRoleConverter(),
                SecurityConfig::authorizationRules);
    }

    static void authorizationRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // Service-to-service: GitWorkspaceCredentialsController admits only the configured client (azp).
        auth.requestMatchers(GET, "/internal/git-workspaces/*/credentials").authenticated();

        // ── admin: platform and project administration ──────────────────────────────
        auth.requestMatchers("/actuator/**").hasRole(ADMIN)
                .requestMatchers("/api/projects/*/git-credentials").hasRole(ADMIN)
                .requestMatchers(POST, "/api/projects").hasRole(ADMIN)
                .requestMatchers(PUT, "/api/projects/*").hasRole(ADMIN)
                .requestMatchers(PATCH, "/api/projects/*/activate", "/api/projects/*/deactivate",
                        "/api/projects/*/new-project").hasRole(ADMIN)
                .requestMatchers(POST, integrationConfigs()).hasRole(ADMIN)
                .requestMatchers(DELETE, integrationConfigs()).hasRole(ADMIN)
                .requestMatchers(PUT, "/api/projects/*/spec-queue/settings").hasRole(ADMIN)
                .requestMatchers(PUT, "/api/opa-config").hasRole(ADMIN)
                .requestMatchers(POST, "/api/skills-marketplaces", "/api/a2a-services").hasRole(ADMIN)
                .requestMatchers(PUT, "/api/skills-marketplaces/*", "/api/a2a-services/*").hasRole(ADMIN)
                .requestMatchers(DELETE, "/api/skills-marketplaces/*", "/api/a2a-services/*").hasRole(ADMIN);

        // ── approver: deciding approval gates ───────────────────────────────────────
        auth.requestMatchers(POST, "/api/agent-runs/*/approval-gate/decision").hasRole(APPROVER);

        // ── operator: running and shaping workflows, specs and spec queues ─────────────
        auth.requestMatchers(POST, "/api/agentic-workflows/start", "/api/agentic-workflows/preflight")
                        .hasRole(OPERATOR)
                .requestMatchers(DELETE, "/api/agent-runs/*").hasRole(OPERATOR)
                .requestMatchers(POST, "/api/workflows", "/api/workflows/import", "/api/workflows/*/start",
                        "/api/workflow-groups").hasRole(OPERATOR)
                .requestMatchers(PUT, "/api/workflows/*", "/api/workflow-groups/*").hasRole(OPERATOR)
                .requestMatchers(DELETE, "/api/workflows/*", "/api/workflow-groups/*").hasRole(OPERATOR)
                .requestMatchers(POST, DEFINITION_COLLECTIONS).hasRole(OPERATOR)
                .requestMatchers(POST, suffixed(DEFINITION_COLLECTIONS, "/generate-from-prompt")).hasRole(OPERATOR)
                .requestMatchers(PUT, suffixed(DEFINITION_COLLECTIONS, "/*")).hasRole(OPERATOR)
                .requestMatchers(DELETE, suffixed(DEFINITION_COLLECTIONS, "/*")).hasRole(OPERATOR)
                .requestMatchers(PUT, "/api/projects/*/spec-files").hasRole(OPERATOR)
                .requestMatchers(POST, "/api/projects/*/spec-init", "/api/projects/*/git-check").hasRole(OPERATOR)
                .requestMatchers(POST, "/api/projects/*/spec-queue/items", "/api/projects/*/spec-queue/items/*/retry",
                        "/api/projects/*/spec-queue/items/*/skip", "/api/projects/*/spec-queue/pause",
                        "/api/projects/*/spec-queue/resume").hasRole(OPERATOR)
                .requestMatchers(PATCH, "/api/projects/*/spec-queue/items/*").hasRole(OPERATOR)
                .requestMatchers(DELETE, "/api/projects/*/spec-queue/items/*").hasRole(OPERATOR)
                .requestMatchers(PUT, "/api/projects/*/spec-queue/order").hasRole(OPERATOR);

        // ── viewer: read-only ───────────────────────────────────────────────────────
        // Listed one by one (no /api/** wildcard), so a new read endpoint is denied until it is added here.
        // Model routing is a side-effect-free computation, exposed as POST for its request body.
        auth.requestMatchers(POST, "/api/model-routing/decision").hasRole(VIEWER)
                .requestMatchers(GET, VIEWER_READS).hasRole(VIEWER)
                .requestMatchers(GET, DEFINITION_COLLECTIONS).hasRole(VIEWER)
                .requestMatchers(GET, suffixed(DEFINITION_COLLECTIONS, "/*")).hasRole(VIEWER);

        // ── deny by default ─────────────────────────────────────────────────────────
        auth.anyRequest().denyAll();
    }

    private static String[] integrationConfigs() {
        return new String[] {"/api/projects/*/hermes-config", "/api/projects/*/jira-config",
                "/api/projects/*/document-folder-config"};
    }

    private static String[] suffixed(String[] bases, String suffix) {
        String[] out = new String[bases.length];
        for (int i = 0; i < bases.length; i++) {
            out[i] = bases[i] + suffix;
        }
        return out;
    }
}
