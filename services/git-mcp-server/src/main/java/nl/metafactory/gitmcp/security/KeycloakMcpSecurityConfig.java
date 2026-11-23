package nl.metafactory.gitmcp.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Mode {@code KEYCLOAK_ENABLED=true}: this server is an OAuth2 resource server and every MCP request (the SSE
 * handshake, the message endpoint and anything else the MCP server registers) needs a valid Keycloak bearer
 * JWT. Missing, malformed, unsigned, expired or not-yet-valid tokens and tokens with a wrong issuer, audience or
 * client ({@code azp}) are refused with 401. There is no fallback to anonymous access: when the signing keys
 * cannot be fetched, requests are refused (HTTP 500: a server-side failure the caller cannot fix by
 * re-authenticating).
 *
 * <p>Anonymous: the health probes only, and {@code /actuator/prometheus} only on a separate management port
 * (the {@code prod} profile's {@code MANAGEMENT_PORT}, never published, scraper-only by network policy).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = McpSecurityProperties.PREFIX, name = McpSecurityProperties.ENABLED_PROPERTY,
        havingValue = "true")
public class KeycloakMcpSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakMcpSecurityConfig.class);

    /** Probe endpoints served without a token; they only report UP/DOWN (details are never shown in prod). */
    static final String[] PUBLIC_HEALTH = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"};

    /**
     * Signing keys from {@code KEYCLOAK_JWK_SET_URI} when set (e.g. Keycloak's internal address), otherwise from
     * the issuer's OpenID configuration. Both are fetched lazily on the first token, so startup does not wait for
     * Keycloak; the {@code iss} claim is always checked against {@code KEYCLOAK_ISSUER_URI}.
     */
    @Bean
    JwtDecoder mcpJwtDecoder(McpSecurityProperties properties) {
        if (properties.hasJwkSetUri()) {
            return withValidators(NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build(), properties);
        }
        // Discovery runs on build(); the supplier defers it to the first token and retries it after a failure.
        return new SupplierJwtDecoder(() -> withValidators(
                NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri()).build(), properties));
    }

    private static JwtDecoder withValidators(NimbusJwtDecoder decoder, McpSecurityProperties properties) {
        decoder.setJwtValidator(McpJwtValidators.forProperties(properties));
        return decoder;
    }

    /**
     * Spring Security 7 serves RFC 9728 protected-resource metadata for every resource server and has no switch
     * to turn it off; this chain answers that path with 401 so only the health probes are anonymous.
     */
    @Bean
    @Order(1)
    SecurityFilterChain protectedResourceMetadataSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain keycloakMcpSecurityFilterChain(HttpSecurity http, JwtDecoder mcpJwtDecoder,
                                                       McpSecurityProperties properties, Environment environment)
            throws Exception {
        log.info("Incoming MCP authentication ENABLED (KEYCLOAK_ENABLED=true): Keycloak JWT required; issuer={}, "
                        + "audience={}, allowed azp={}, signing keys from {}",
                properties.getIssuerUri(), properties.getExpectedAudience(), properties.allowedClientIds(),
                properties.hasJwkSetUri() ? properties.getJwkSetUri() : "issuer discovery");
        RequestMatcher scrape = metricsScrapeMatcher(environment);
        return http
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> {
                    // ERROR/ASYNC dispatches only continue a REQUEST dispatch that was already authorised (the SSE
                    // stream is async); they never let an unauthenticated request in.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll();
                    auth.requestMatchers(PUBLIC_HEALTH).permitAll();
                    if (scrape != null) {
                        auth.requestMatchers(scrape).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(mcpJwtDecoder)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * {@code /actuator/prometheus} on the separate management port only; null when there is none (then the
     * endpoint needs a token like everything else). Same rule as the other OpenJCockpit services.
     */
    static RequestMatcher metricsScrapeMatcher(Environment environment) {
        Integer port = environment.getProperty("management.server.port", Integer.class);
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        if (port == null || port <= 0 || port.equals(serverPort)) {
            return null;
        }
        return request -> request.getLocalPort() == port && "/actuator/prometheus".equals(request.getRequestURI());
    }
}
