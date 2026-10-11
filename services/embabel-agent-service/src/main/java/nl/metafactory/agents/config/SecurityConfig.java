package nl.metafactory.agents.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import nl.metafactory.agents.security.RunnerAwareJwtDecoder;
import nl.metafactory.agents.security.SpecQueueRunnerTokenProperties;
import nl.metafactory.common.security.ResourceServerSecurityConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
@Import(ResourceServerSecurityConfig.class)
public class SecurityConfig {

    public static final String SPEC_QUEUE_RUNNER = "SPEC_QUEUE_RUNNER";

    /** Authenticated, non-anonymous and not the runner service identity. */
    private static final AuthorizationManager<RequestAuthorizationContext> HUMAN_ONLY =
            new WebExpressionAuthorizationManager("isAuthenticated() and !hasAuthority('" + SPEC_QUEUE_RUNNER + "')");

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SpecQueueRunnerTokenProperties runnerProperties) throws Exception {
        return ResourceServerSecurityConfig.buildApiChain(http, jwtAuthenticationConverter(runnerProperties), auth -> auth
                // Let the real error status (404/400) reach the runner instead of masking it on the error dispatch.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // Must precede the {id} pattern below.
                .requestMatchers(HttpMethod.GET, "/api/workflows/export").access(HUMAN_ONLY)
                // The only routes the spec-queue runner identity may reach.
                .requestMatchers(HttpMethod.POST, "/api/workflows/*/start").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/workflows/*", "/api/workflow-groups/*",
                        "/api/agent-runs/*").authenticated()
                .anyRequest().access(HUMAN_ONLY));
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

    /** Runner tokens get only {@link #SPEC_QUEUE_RUNNER}; every other token keeps the default (human) authorities. */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(SpecQueueRunnerTokenProperties props) {
        JwtAuthenticationConverter humanConverter = new JwtAuthenticationConverter();
        return jwt -> props.isConfigured() && props.getIssuer().equals(jwt.getClaimAsString("iss"))
                ? new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(SPEC_QUEUE_RUNNER)))
                : humanConverter.convert(jwt);
    }
}
