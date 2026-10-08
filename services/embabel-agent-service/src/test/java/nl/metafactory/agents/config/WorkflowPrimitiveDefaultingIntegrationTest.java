package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2 remediation (integration review, upgrade-embabel-spring-ai-spring-boot-latest, F-2):
 * Jackson 3's auto-configured {@code JsonMapper} defaults
 * {@code DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES} to enabled (Jackson 2 defaulted it to
 * disabled). Left unfixed at the HTTP boundary, {@code POST /api/workflows} omitting the optional
 * primitive {@code promptRequired} field 400s with a raw Jackson diagnostic before bean validation
 * even runs. {@code spring.jackson.deserialization.fail-on-null-for-primitives: false} in
 * {@code application.yml} restores the pre-upgrade tolerant behaviour: this test proves it, using a
 * real embedded servlet container (not a {@code @WebMvcTest}/mocked-service slice), following the
 * same pattern as {@link ErrorResponseContractIntegrationTest}.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WorkflowPrimitiveDefaultingIntegrationTest {

    @TempDir
    static Path workflowDefinitionsDir;

    @DynamicPropertySource
    static void workflowDefinitionsPath(DynamicPropertyRegistry registry) {
        registry.add("openjcockpit.workflow-definitions.path", () -> workflowDefinitionsDir.toString());
    }

    @MockitoBean
    ModelProvider modelProvider;

    @Autowired
    TestRestTemplate restTemplate;

    @TestConfiguration
    static class RealJwtDecoderOverride {

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

    private Map<String, Object> validWorkflowBodyOmittingPromptRequired(String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", "Onboarding");
        body.put("projectName", "Noordzee Logistics");
        body.put("description", "desc");
        body.put("agentIds", List.of("requirement"));
        return body;
    }

    @Test
    void omittingOptionalPrimitivePromptRequiredFieldNowSucceedsInsteadOf400ing() {
        Map<String, Object> body = validWorkflowBodyOmittingPromptRequired("wf-primitive-default-it-1");

        ResponseEntity<Map> response = restTemplate.exchange("/api/workflows", HttpMethod.POST,
                new HttpEntity<>(body, authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode())
                .as("omitting the optional primitive 'promptRequired' field must deserialize to its "
                        + "default value and proceed past Jackson, matching pre-upgrade behaviour, "
                        + "not 400 with a raw Jackson diagnostic")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("promptRequired", false);
    }

    @Test
    void genuineValidationFailureStillProducesRealValidationDetailNotAJacksonDiagnostic() {
        Map<String, Object> body = validWorkflowBodyOmittingPromptRequired("wf-primitive-default-it-2");
        body.put("agentIds", List.of());

        ResponseEntity<Map> response = restTemplate.exchange("/api/workflows", HttpMethod.POST,
                new HttpEntity<>(body, authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Object message = response.getBody() != null ? response.getBody().get("message") : null;
        assertThat(message).isInstanceOf(String.class);
        assertThat((String) message)
                .as("a genuine validation failure must still carry real, specific validation detail")
                .contains("At least one pipeline agent must be selected")
                .doesNotContain("FAIL_ON_NULL_FOR_PRIMITIVES")
                .doesNotContain("JSON parse error");
    }
}
