package nl.metafactory.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security shared by the services: the OAuth protected-resource-metadata
 * chain and the issuer-validating {@link JwtDecoder}. A service {@code @Import}s this class and
 * defines its own {@code @Order(2)} API chain, finishing it with {@link #buildApiChain}.
 */
@Configuration
public class ResourceServerSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain protectedResourceMetadataSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/.well-known/oauth-protected-resource/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${openjcockpit.security.jwt-issuer}") String issuerUri
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    /**
     * Applies the common API rules (stateless, JWT-authenticated, public health/info) to a chain the
     * service has already customised, e.g. with CORS, and builds it.
     */
    public static SecurityFilterChain buildApiChain(HttpSecurity http) throws Exception {
        return buildApiChain(http, new JwtAuthenticationConverter());
    }

    /** As {@link #buildApiChain(HttpSecurity)}, with a service-specific JWT-to-authentication converter. */
    public static SecurityFilterChain buildApiChain(HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter) throws Exception {
        return buildApiChain(http, jwtConverter, auth -> auth.anyRequest().authenticated());
    }

    /**
     * As {@link #buildApiChain(HttpSecurity, Converter)}, with service-specific request rules applied after
     * the public health/info matchers; {@code rules} must end with an {@code anyRequest()} rule.
     */
    public static SecurityFilterChain buildApiChain(HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> rules)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    rules.customize(auth);
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
