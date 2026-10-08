package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalDecisionRequest;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ApplicationContext;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Embabel 1.5.1 ships zero MVC controllers, so Boot's own {@code BasicErrorController}
 * auto-configures itself with no help; this is a permanent regression detector.
 *
 * <p>Before the platform removed its own custom {@code ErrorController} bean, that bean
 * satisfied Spring Boot's {@code ErrorMvcAutoConfiguration.basicErrorController()}
 * {@code @ConditionalOnMissingBean} guard, so Boot's own {@code BasicErrorController} was
 * never created and every {@code /error} dispatch — including every 400/404/409 raised by
 * this application's own controllers — was served by the platform controller instead. It
 * hard-coded a body containing only {@code status}/{@code error}/{@code path} and never
 * read {@code jakarta.servlet.error.message}, so {@code message} was always absent, no
 * matter what {@code server.error.include-message} was set to.
 *
 * <p>Both the {@code @WebMvcTest} slice tests (which never load the platform's own beans,
 * so they can never observe this bean-shadowing at all) and the {@code MockWebServer}-based
 * relay tests in {@code ai-control-service} (which assert against a hand-authored response
 * body, not a body the real server produced) are structurally incapable of catching this —
 * this is exactly the failure mode QA identified. Only a full {@code @SpringBootTest} with
 * a real embedded servlet container, loading this application's actual beans (including
 * whichever {@code ErrorController} bean wins), and a real HTTP client actually exercises
 * the code path that broke.
 *
 * <p>{@code spring.web.error.include-message} is set explicitly via {@link TestPropertySource}
 * rather than relying on {@code src/main/resources/application.yml}: Maven's test classpath
 * places {@code src/test/resources} ahead of {@code src/main/resources}, so the test-scoped
 * {@code application.yml} (which has no error-include section) silently shadows the
 * production one for any test that boots the full Spring context — a separate, pre-existing
 * test-infrastructure quirk that is orthogonal to this defect (the real packaged/deployed jar
 * has only the production {@code application.yml} and was independently confirmed, live, to
 * exhibit the exact same bug before this fix — see the QA report and this delivery's backend
 * report).
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.web.error.include-message=always")
class ErrorResponseContractIntegrationTest {

    @TempDir
    static Path workflowDefinitionsDir;

    @DynamicPropertySource
    static void workflowDefinitionsPath(DynamicPropertyRegistry registry) {
        // Isolates this test's workflow writes from other tests/the shared /tmp default.
        registry.add("openjcockpit.workflow-definitions.path", () -> workflowDefinitionsDir.toString());
    }

    @MockitoBean
    ModelProvider modelProvider;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ApplicationContext applicationContext;

    @TestConfiguration
    static class RealJwtDecoderOverride {

        /**
         * A real embedded servlet container enforces the real {@code SecurityFilterChain}
         * (unlike a {@code @WebMvcTest} slice), so a real bearer token is required to reach the
         * validation logic under test. This stands in for Keycloak's JWKS-backed decoder (which
         * this test cannot depend on) while keeping the entire authenticated-request path real.
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

    @Test
    void realHttpResponseForInvalidApprovalGatePlacementIncludesTheMessageField() {
        var invalidWorkflow = new WorkflowDefinition("wf-error-handling-it", "Onboarding", "Noordzee Logistics",
                null, "desc", List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null,
                "ACTIVE", null, null, new ApprovalGateConfig(true, "realisation"), null);

        ResponseEntity<Map> response = restTemplate.exchange("/api/workflows", HttpMethod.POST,
                new HttpEntity<>(invalidWorkflow, authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Object message = response.getBody() != null ? response.getBody().get("message") : null;
        assertThat(message).as("real HTTP error body must contain a non-blank 'message' field")
                .isInstanceOf(String.class);
        assertThat((String) message)
                .contains("realisation")
                .contains("not among this workflow's selected agents");
    }

    @Test
    void realHttpResponseForUnknownApprovalRunReturns404WithMessage() {
        var request = new ApprovalDecisionRequest(ApprovalDecisionKind.ACCEPT, 1, null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/agent-runs/does-not-exist/approval-gate/decision", HttpMethod.POST,
                new HttpEntity<>(request, authenticatedJsonHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Object message = response.getBody() != null ? response.getBody().get("message") : null;
        assertThat(message).as("real HTTP 404 body must contain a non-blank 'message' field")
                .isInstanceOf(String.class);
        assertThat((String) message).contains("Run not found: does-not-exist");
    }

    @Test
    void exactlyOneErrorControllerBeanExistsAndItIsBootsBasicErrorController() {
        String[] errorControllerBeans = applicationContext.getBeanNamesForType(
                org.springframework.boot.webmvc.error.ErrorController.class);

        assertThat(errorControllerBeans)
                .as("exactly one ErrorController bean must exist")
                .hasSize(1);

        Object errorControllerBean = applicationContext.getBean(errorControllerBeans[0]);
        assertThat(errorControllerBean)
                .as("the single ErrorController bean must be Boot's BasicErrorController")
                .isInstanceOf(org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController.class);
    }
}
