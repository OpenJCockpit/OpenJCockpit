package nl.metafactory.agents.config;

import java.util.ArrayList;
import java.util.List;
import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.security.RunnerAwareJwtDecoder;
import nl.metafactory.agents.security.SpecQueueRunnerTokenProperties;
import nl.metafactory.common.security.KeycloakRealmRoleConverter;
import nl.metafactory.common.security.OpenJCockpitRoles;
import nl.metafactory.common.security.ResourceServerSecurityConfig;
import nl.metafactory.common.security.ServiceCallers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static nl.metafactory.common.security.OpenJCockpitRoles.ADMIN;
import static nl.metafactory.common.security.OpenJCockpitRoles.APPROVER;
import static nl.metafactory.common.security.OpenJCockpitRoles.OPERATOR;

/**
 * Defence-in-depth authorisation of embabel-agent-service. ai-control-service relays the signed-in user's
 * token, so human requests carry their Keycloak realm roles ({@link OpenJCockpitRoles}) here too, and the
 * mutations that matter most are re-checked: deciding an approval gate needs approver, changing or running
 * workflows needs operator, and OPA / A2A configuration needs admin. Two service identities are recognised:
 * the spec-queue runner ({@link #SPEC_QUEUE_RUNNER}, limited to a fixed route list) and ai-control-service's
 * own client ({@link #AI_CONTROL_SERVICE}, by {@code azp}), which may start and stop the workflow runs of
 * git-workspace jobs. {@code /internal/**} is authorised by {@code azp} in its controllers. Remaining
 * routes (reads) stay open to any authenticated caller that is not the runner.
 */
@Configuration
@EnableWebSecurity
@Import(ResourceServerSecurityConfig.class)
public class SecurityConfig {

    public static final String SPEC_QUEUE_RUNNER = "SPEC_QUEUE_RUNNER";

    /** ai-control-service's client-credentials identity (token {@code azp} = the trusted git-workspace client). */
    public static final String AI_CONTROL_SERVICE = "AI_CONTROL_SERVICE";

    private static final String[] DEFINITION_COLLECTIONS = {
            "/api/agent-definitions", "/api/skill-definitions", "/api/subagent-definitions"};

    /** Authenticated, non-anonymous and not the runner service identity. */
    private static final AuthorizationManager<RequestAuthorizationContext> HUMAN_ONLY =
            new WebExpressionAuthorizationManager("isAuthenticated() and !hasAuthority('" + SPEC_QUEUE_RUNNER + "')");

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SpecQueueRunnerTokenProperties runnerProperties,
                                            GitWorkspaceProperties gitWorkspaceProperties,
                                            RoleHierarchy roleHierarchy) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> startRun = anyAuthority(roleHierarchy,
                SPEC_QUEUE_RUNNER, AI_CONTROL_SERVICE, OpenJCockpitRoles.authority(OPERATOR));
        AuthorizationManager<RequestAuthorizationContext> stopRun = anyAuthority(roleHierarchy,
                AI_CONTROL_SERVICE, OpenJCockpitRoles.authority(OPERATOR));
        return ResourceServerSecurityConfig.buildApiChain(http,
                jwtAuthenticationConverter(runnerProperties, gitWorkspaceProperties.getTrustedClientId()), auth -> auth
                // Service-to-service; each controller admits only its configured client (azp).
                .requestMatchers("/internal/**").access(HUMAN_ONLY)
                // Must precede the {id} pattern below.
                .requestMatchers(HttpMethod.GET, "/api/workflows/export").access(HUMAN_ONLY)
                // Approval decisions: approver (or admin) only.
                .requestMatchers(HttpMethod.POST, "/api/agent-runs/*/approval-gate/decision").hasRole(APPROVER)
                // Starting / stopping runs: the runner (start only), ai-control's service identity, or an operator.
                .requestMatchers(HttpMethod.POST, "/api/workflows/*/start").access(startRun)
                .requestMatchers(HttpMethod.DELETE, "/api/agent-runs/*").access(stopRun)
                // The only other routes the spec-queue runner identity may reach.
                .requestMatchers(HttpMethod.GET, "/api/workflows/*", "/api/workflow-groups/*",
                        "/api/agent-runs/*").authenticated()
                // Workflow, workflow-group and definition mutations: operator.
                .requestMatchers(HttpMethod.POST, "/api/workflows", "/api/workflows/import", "/api/workflow-groups")
                        .hasRole(OPERATOR)
                .requestMatchers(HttpMethod.PUT, "/api/workflows/*", "/api/workflow-groups/*").hasRole(OPERATOR)
                .requestMatchers(HttpMethod.DELETE, "/api/workflows/*", "/api/workflow-groups/*").hasRole(OPERATOR)
                .requestMatchers(HttpMethod.POST, DEFINITION_COLLECTIONS).hasRole(OPERATOR)
                .requestMatchers(HttpMethod.POST, suffixed("/generate-from-prompt")).hasRole(OPERATOR)
                .requestMatchers(HttpMethod.PUT, suffixed("/*")).hasRole(OPERATOR)
                .requestMatchers(HttpMethod.DELETE, suffixed("/*")).hasRole(OPERATOR)
                // Platform configuration: admin.
                .requestMatchers(HttpMethod.PUT, "/api/opa-config").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/a2a-services").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/a2a-services/*").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/a2a-services/*").hasRole(ADMIN)
                .anyRequest().access(HUMAN_ONLY));
    }

    private static AuthorizationManager<RequestAuthorizationContext> anyAuthority(RoleHierarchy roleHierarchy,
                                                                                  String... authorities) {
        AuthorityAuthorizationManager<RequestAuthorizationContext> manager =
                AuthorityAuthorizationManager.hasAnyAuthority(authorities);
        manager.setRoleHierarchy(roleHierarchy);
        return manager;
    }

    private static String[] suffixed(String suffix) {
        String[] out = new String[DEFINITION_COLLECTIONS.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = DEFINITION_COLLECTIONS[i] + suffix;
        }
        return out;
    }

    /**
     * Wraps the shared issuer-validating decoder (bean {@code jwtDecoder}, from
     * {@link ResourceServerSecurityConfig}) so runner tokens are also accepted when configured.
     */
    @Bean
    static BeanPostProcessor runnerAwareJwtDecoderPostProcessor(ObjectProvider<SpecQueueRunnerTokenProperties> props) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!"jwtDecoder".equals(beanName) || !(bean instanceof JwtDecoder decoder)) {
                    return bean;
                }
                SpecQueueRunnerTokenProperties runnerProperties = props.getObject();
                return runnerProperties.isConfigured() ? new RunnerAwareJwtDecoder(decoder, runnerProperties) : bean;
            }
        };
    }

    /** As {@link #jwtAuthenticationConverter(SpecQueueRunnerTokenProperties, String)} without an ai-control identity. */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(SpecQueueRunnerTokenProperties props) {
        return jwtAuthenticationConverter(props, null);
    }

    /**
     * Runner tokens get only {@link #SPEC_QUEUE_RUNNER}. Every other token gets its scopes and its OpenJCockpit
     * realm roles ({@link OpenJCockpitRoles#ALL}; other realm roles are dropped); one whose {@code azp} is
     * ai-control-service's client additionally gets {@link #AI_CONTROL_SERVICE}.
     */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(SpecQueueRunnerTokenProperties props,
                                                                                  String aiControlClientId) {
        KeycloakRealmRoleConverter keycloakConverter = new KeycloakRealmRoleConverter();
        List<String> knownRoles = OpenJCockpitRoles.ALL.stream().map(OpenJCockpitRoles::authority).toList();
        return jwt -> {
            if (props.isConfigured() && props.getIssuer().equals(jwt.getClaimAsString("iss"))) {
                return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(SPEC_QUEUE_RUNNER)));
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (GrantedAuthority authority : keycloakConverter.convert(jwt).getAuthorities()) {
                String name = authority.getAuthority();
                if (!name.startsWith("ROLE_") || knownRoles.contains(name)) {
                    authorities.add(authority);
                }
            }
            if (ServiceCallers.is(jwt, aiControlClientId)) {
                authorities.add(new SimpleGrantedAuthority(AI_CONTROL_SERVICE));
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
