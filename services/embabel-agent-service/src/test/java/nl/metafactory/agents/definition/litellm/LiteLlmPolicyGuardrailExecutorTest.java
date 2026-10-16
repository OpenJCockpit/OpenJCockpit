package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract: LiteLLM v1.101.2 {@code POST /policies/resolve} → {@code PolicyResolveResponse}. */
class LiteLlmPolicyGuardrailExecutorTest {

    MockWebServer server;
    LiteLlmPolicyGuardrailExecutor executor;
    GuardrailPolicyEnforcer enforcer;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        var http = new LiteLlmHttpClient(server.url("/").toString(), "sk-test", Duration.ofSeconds(1), Duration.ofMillis(500));
        executor = new LiteLlmPolicyGuardrailExecutor("gateway-pii-filter", "openjcockpit-pii", http, Clock.systemUTC());
        enforcer = new GuardrailPolicyEnforcer(List.of(executor), Set.of());
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    static MockResponse resolved(String... guardrails) {
        String list = String.join(",", java.util.Arrays.stream(guardrails).map(g -> "\"" + g + "\"").toList());
        return new MockResponse().setBody("{\"effective_guardrails\":[" + list + "],\"matched_policies\":"
                + "[{\"policy_name\":\"openjcockpit-agent-pii\",\"matched_via\":\"model:customer-chat\","
                + "\"guardrails_added\":[" + list + "]}]}");
    }

    @Test
    void bindsOnlyWhenLiteLlmConfirmsThePolicyForTheModel() throws Exception {
        server.enqueue(resolved("openjcockpit-pii"));
        var binding = enforcer.enforce("customer-support", "customer-chat", List.of("gateway-pii-filter"));

        assertThat(binding.executors()).containsEntry("gateway-pii-filter", "litellm-policy:openjcockpit-pii");
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/policies/resolve");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"model\":\"customer-chat\"}");
    }

    @Test
    void policyNotAttachedToModelBlocksTheRun() {
        server.enqueue(resolved("some-other-guardrail"));
        assertThatThrownBy(() -> enforcer.enforce("a", "customer-chat", List.of("gateway-pii-filter")))
                .isInstanceOf(AgentResolutionException.class)
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.GUARDRAIL_MISSING);
    }

    @Test
    void gatewayErrorsFailClosed() {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS));
        for (int i = 0; i < 3; i++) {
            assertThat(executor.operationalFor("customer-chat")).isFalse();
        }
    }

    @Test
    void withoutModelAliasCoverageIsNeverAssumed() {
        assertThat(executor.operational()).isFalse();
        assertThatThrownBy(() -> enforcer.enforce("a", List.of("gateway-pii-filter")))
                .isInstanceOf(AgentResolutionException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void positiveVerificationIsCachedBriefly() {
        server.enqueue(resolved("openjcockpit-pii"));
        assertThat(executor.operationalFor("customer-chat")).isTrue();
        assertThat(executor.operationalFor("customer-chat")).isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void withoutAModelOrWithoutEffectiveGuardrailsTheGatewayIsNotVerified() {
        assertThat(executor.operationalFor(null)).isFalse();

        server.enqueue(new MockResponse().setBody("{\"matched_policies\":[]}"));
        assertThat(executor.operationalFor("customer-chat")).isFalse();
    }
}
