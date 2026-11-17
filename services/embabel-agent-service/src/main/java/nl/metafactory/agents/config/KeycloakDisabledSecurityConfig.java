package nl.metafactory.agents.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import nl.metafactory.common.security.OpenJCockpitRoles;
import nl.metafactory.common.security.ProductionConfigGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code ENABLE_KEYCLOAK=false}: runs without Keycloak. No token is validated and every request is permitted,
 * authenticated as one fixed local identity holding every OpenJCockpit role, so runs and approvals are still
 * attributed to a user. The internal endpoints skip their caller ({@code azp}) check in this mode. Refused next
 * to {@code prod}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnBooleanProperty(name = SecurityConfig.KEYCLOAK_ENABLED, havingValue = false)
@Import(ProductionConfigGuard.class)
public class KeycloakDisabledSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakDisabledSecurityConfig.class);

    public KeycloakDisabledSecurityConfig(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Production configuration rejected (profile 'prod'): "
                    + SecurityConfig.KEYCLOAK_ENABLED + " is false, every request would be unauthenticated");
        }
    }

    @Bean
    SecurityFilterChain keycloakDisabledSecurityFilterChain(HttpSecurity http, Jwt localIdentity) throws Exception {
        log.warn("security.keycloak-disabled: authentication is disabled, every request runs as '{}'",
                localIdentity.getSubject());
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new LocalIdentityFilter(localIdentity), AnonymousAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    Jwt localIdentity(@Value("${openjcockpit.dev.identity.username:developer}") String username) {
        return Jwt.withTokenValue("local")
                .header("alg", "none")
                .subject(username)
                .claim("preferred_username", username)
                .issuedAt(Instant.EPOCH)
                .build();
    }

    static final class LocalIdentityFilter extends OncePerRequestFilter {

        private static final List<GrantedAuthority> ROLES = OpenJCockpitRoles.ALL.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(OpenJCockpitRoles.authority(role)))
                .toList();

        private final Jwt identity;

        LocalIdentityFilter(Jwt identity) {
            this.identity = identity;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            SecurityContextHolder.getContext().setAuthentication(
                    new JwtAuthenticationToken(identity, ROLES, identity.getSubject()));
            chain.doFilter(request, response);
        }
    }
}
