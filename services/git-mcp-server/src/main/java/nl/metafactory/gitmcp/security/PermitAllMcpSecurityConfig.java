package nl.metafactory.gitmcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mode {@code KEYCLOAK_ENABLED=false} (the default): every request is permitted without authentication. No
 * resource server is configured, so there is no bearer-token filter and no {@link
 * org.springframework.security.oauth2.jwt.JwtDecoder}; an {@code Authorization} header is ignored and Keycloak
 * is never contacted for incoming requests. Local development or a trusted, network-isolated deployment only.
 *
 * <p>Outgoing workspace credentials (this server -> ai-control-service) are unaffected by this mode.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = McpSecurityProperties.PREFIX, name = McpSecurityProperties.ENABLED_PROPERTY,
        havingValue = "false", matchIfMissing = true)
public class PermitAllMcpSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(PermitAllMcpSecurityConfig.class);

    @Bean
    SecurityFilterChain permitAllMcpSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("Incoming MCP authentication DISABLED (KEYCLOAK_ENABLED=false): every MCP request is permitted "
                + "without a token. Use only for local development or a trusted, network-isolated deployment.");
        return http
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
