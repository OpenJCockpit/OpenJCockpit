package nl.metafactory.agentruntime.config;

import nl.metafactory.common.security.ResourceServerSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every endpoint except health/info requires a Keycloak JWT (same issuer as the other services). The
 * internal API additionally authorises the caller by {@code azp} in its controllers; inbound A2A takes the
 * tenant from the token. Being on the same network as another service grants nothing.
 */
@Configuration
@EnableWebSecurity
@Import(ResourceServerSecurityConfig.class)
public class SecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Compose/Kubernetes probes carry no token; readiness/liveness report UP/DOWN only.
        return ResourceServerSecurityConfig.buildApiChain(http, new JwtAuthenticationConverter(), auth -> auth
                .requestMatchers("/actuator/health/readiness", "/actuator/health/liveness").permitAll()
                .anyRequest().authenticated());
    }
}
