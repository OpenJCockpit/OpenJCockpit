package nl.metafactory.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(ResourceServerSecurityConfigTest.TestApp.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks",
        "openjcockpit.security.jwt-issuer=http://issuer.example",
        // a separate management port activates the metrics-scrape rule (MetricsScrapeMatcherTest)
        "management.server.port=9090",
        "server.port=8080"
})
class ResourceServerSecurityConfigTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(ResourceServerSecurityConfig.class)
    static class TestApp {
        @Bean
        @Order(2)
        SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
            return ResourceServerSecurityConfig.buildApiChain(http);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/api/probe")
        ResponseEntity<String> probe() {
            return ResponseEntity.ok("ok");
        }

        @GetMapping("/actuator/health")
        ResponseEntity<String> health() {
            return ResponseEntity.ok("UP");
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder jwtDecoder;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/probe").with(jwt())).andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectedResourceMetadataIsNotAnonymous() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/.well-known/oauth-protected-resource/x")).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtDecoderRejectsMalformedTokens() {
        assertThat(jwtDecoder).isNotNull();
        assertThatThrownBy(() -> jwtDecoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
