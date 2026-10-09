package nl.metafactory.agents.config;

import jakarta.servlet.DispatcherType;
import nl.metafactory.agents.security.RunnerAwareJwtDecoder;
import nl.metafactory.agents.security.SpecQueueRunnerTokenProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String SPEC_QUEUE_RUNNER = "SPEC_QUEUE_RUNNER";

    /** Authenticated, non-anonymous and not the runner service identity. */
    private static final AuthorizationManager<RequestAuthorizationContext> HUMAN_ONLY =
            new WebExpressionAuthorizationManager("isAuthenticated() and !hasAuthority('" + SPEC_QUEUE_RUNNER + "')");

    @Bean
    @Order(1)
    SecurityFilterChain protectedResourceMetadataSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/.well-known/oauth-protected-resource/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SpecQueueRunnerTokenProperties runnerProperties) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Let the real error status (404/400) reach the runner instead of masking it on the error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Must precede the {id} pattern below.
                        .requestMatchers(HttpMethod.GET, "/api/workflows/export").access(HUMAN_ONLY)
                        // The only routes the spec-queue runner identity may reach.
                        .requestMatchers(HttpMethod.POST, "/api/workflows/*/start").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/workflows/*", "/api/workflow-groups/*",
                                "/api/agent-runs/*").authenticated()
                        .anyRequest().access(HUMAN_ONLY)
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter(runnerProperties))))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${openjcockpit.security.jwt-issuer}") String issuerUri,
            SpecQueueRunnerTokenProperties runnerProperties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return runnerProperties.isConfigured() ? new RunnerAwareJwtDecoder(decoder, runnerProperties) : decoder;
    }

    /** Runner tokens get only {@link #SPEC_QUEUE_RUNNER}; every other token keeps the default (human) authorities. */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(SpecQueueRunnerTokenProperties props) {
        JwtAuthenticationConverter humanConverter = new JwtAuthenticationConverter();
        return jwt -> props.isConfigured() && props.getIssuer().equals(jwt.getClaimAsString("iss"))
                ? new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(SPEC_QUEUE_RUNNER)))
                : humanConverter.convert(jwt);
    }
}
