package nl.metafactory.aicontrol.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-QA-1 (workflow-approval-gate QA report §9): a REAL embedded servlet container test for
 * {@code ai-control-service}, using a REAL {@link TestRestTemplate} HTTP round trip, that exercises
 * the FULL relay chain end to end:
 *
 * <ol>
 *   <li>a {@link MockWebServer} stands in for {@code embabel-agent-service}'s network boundary
 *       only — network isolation is the only thing it replaces;</li>
 *   <li>it returns a body shaped exactly like embabel-agent-service's REAL (now-fixed) error
 *       response: the standard Spring Boot shape with a genuine {@code message} field, confirmed
 *       live against a real running container (see this delivery's backend report);</li>
 *   <li>{@link EmbabelAgentClient}'s OWN, real, unmocked {@code extractMessage(...)} logic then
 *       parses that body and builds ITS OWN {@code ResponseStatusException};</li>
 *   <li>ai-control-service's own REAL embedded Tomcat, REAL Spring Security filter chain, and REAL
 *       {@code BasicErrorController}/{@code DefaultErrorAttributes} (governed by ITS OWN
 *       {@code spring.web.error.include-message: always} — see the {@code @TestPropertySource}
 *       below) then serialise that exception into an actual HTTP response body.</li>
 * </ol>
 *
 * <p>Before this fix, this test's body would have shown the ENTIRE raw JSON blob from embabel as
 * the {@code message} value (a plain-text mock body would also have masked that defect — the same
 * "vacuous test" shape QA identified — which is why the enqueued body here is realistic JSON, not
 * hand-waved plain text), because {@code EmbabelAgentClient.badRequest(...)} used to relay
 * {@code e.getResponseBodyAsString()} verbatim, and because ai-control-service had no
 * {@code server.error.include-message} setting of its own at all (so even a clean upstream message
 * would have been stripped again on ai-control-service's own {@code /error} dispatch). This test
 * asserts the real, final HTTP body the dashboard would actually receive contains ONLY the clean
 * upstream message text — not the surrounding JSON envelope.
 */
// spring.web.error.include-message is set explicitly here (rather than relying on
// src/main/resources/application.yml) because Maven's test classpath places
// src/test/resources ahead of src/main/resources, so the test-scoped application.yml silently
// shadows the production one for any test that boots the full Spring context. This mirrors
// embabel-agent-service's equivalent test/production split.
//
// Discovered during the Spring Boot 3.5.0 -> 4.1.1 upgrade: BasicErrorController's own
// message-inclusion policy is governed by org.springframework.boot.autoconfigure.web.WebProperties
// (bound at "spring.web.error.*"), NOT by the now-removed ServerProperties#getError() (which was
// bound at "server.error.*"). Both keys are set below: "server.error.include-message" is kept for
// parity with production application.yml and any code that still binds ErrorProperties directly
// to "server.error" (see ProductionApplicationYamlConfigTest); "spring.web.error.include-message"
// is the property that actually determines whether the real HTTP response below carries a
// "message" field.
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"server.error.include-message=always", "spring.web.error.include-message=always"})
class EmbabelErrorRelayIntegrationTest {

    private static MockWebServer embabel;

    @DynamicPropertySource
    static void embabelBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        embabel = new MockWebServer();
        embabel.start();
        registry.add("metafactory.embabel-agent-service.base-url", () -> embabel.url("/").toString());
    }

    @AfterAll
    static void tearDown() throws IOException {
        // The MockWebServer is created once in the static @DynamicPropertySource method, and the
        // Spring context (and the WebClient bean pointing at its URL) is cached and reused across
        // this class's test methods — shutting it down per-test would break later tests sharing
        // the same cached context.
        embabel.shutdown();
    }

    @Autowired
    TestRestTemplate restTemplate;

    @TestConfiguration
    static class RealJwtDecoderOverride {

        /**
         * A real embedded servlet container enforces the real {@code SecurityFilterChain}, so a
         * real bearer token is required. Stands in for Keycloak's JWKS-backed decoder.
         */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "qa-tester-sub")
                    .claim("preferred_username", "qa-tester")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    private HttpHeaders authenticatedJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("any-token-value-the-test-decoder-accepts-unconditionally");
        return headers;
    }

    private WorkflowDefinitionDto workflow(String id) {
        return new WorkflowDefinitionDto(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null,
                null, null);
    }

    @Test
    void realHttpResponseRelaysOnlyTheCleanUpstreamMessageNotTheRawJsonBody() {
        // Exactly the shape embabel-agent-service's real, fixed BasicErrorController now produces.
        embabel.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"timestamp\":\"2026-01-01T00:00:00.000+00:00\",\"status\":400,"
                        + "\"error\":\"Bad Request\"," 
                        + "\"message\":\"Approval gate placement stage 'realisation' is not among this workflow's selected agents: [requirement]\"," 
                        + "\"path\":\"/api/workflows\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ResponseEntity<Map> response = restTemplate.exchange("/api/workflows", HttpMethod.POST,
                new HttpEntity<>(workflow("wf-relay-it"), authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Object message = response.getBody() != null ? response.getBody().get("message") : null;
        assertThat(message).as("real HTTP error body must contain the clean upstream message")
                .isEqualTo("Approval gate placement stage 'realisation' is not among this workflow's selected agents: [requirement]");
        assertThat((String) message).doesNotContain("\"timestamp\"", "\"status\"", "\"path\"");
    }

    @Test
    void realHttpResponseFallsBackToAGenericReasonWhenUpstreamBodyIsNotJson() {
        embabel.enqueue(new MockResponse().setResponseCode(400).setBody("not json at all"));

        ResponseEntity<Map> response = restTemplate.exchange("/api/workflows", HttpMethod.POST,
                new HttpEntity<>(workflow("wf-relay-it-2"), authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Object message = response.getBody() != null ? response.getBody().get("message") : null;
        assertThat(message).isEqualTo("Bad Request");
    }
}
