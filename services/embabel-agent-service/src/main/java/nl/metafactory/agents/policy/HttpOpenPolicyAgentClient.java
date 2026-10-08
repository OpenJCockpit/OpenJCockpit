package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.OpaDecisionRequest;
import nl.metafactory.agents.policy.model.OpaDecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient-based OPA client. The base URL is read from OpaProperties on every call rather than
 * baked into the WebClient at construction time, because OpaProperties can be mutated at runtime
 * via the Workflow Configuration UI (see OpaConfigController) without a service restart.
 */
@Component
public class HttpOpenPolicyAgentClient implements OpenPolicyAgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOpenPolicyAgentClient.class);

    private final WebClient webClient;
    private final OpaProperties properties;

    public HttpOpenPolicyAgentClient(WebClient.Builder webClientBuilder, OpaProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public boolean isReachable() {
        try {
            webClient.get()
                    .uri(properties.getBaseUrl() + properties.getHealthPath())
                    .headers(this::applyAuth)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            return true;
        } catch (Exception e) {
            log.debug("OPA health check failed: {}", rootMessage(e));
            return false;
        }
    }

    @Override
    public OpaDecisionResponse evaluate(OpaDecisionRequest request) {
        String workflowId = request.input() != null ? request.input().workflowId() : "unknown";
        String action = request.input() != null ? request.input().action() : "unknown";
        log.info("Evaluating OPA policy for workflow {} action {}", workflowId, action);
        try {
            return webClient.post()
                    .uri(properties.getBaseUrl() + properties.getPolicyPath())
                    .headers(this::applyAuth)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpaDecisionResponse.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
        } catch (Exception e) {
            log.warn("OPA policy evaluation failed for workflow {} action {}: {}", workflowId, action, rootMessage(e));
            throw new OpaClientException("OPA policy evaluation failed: " + rootMessage(e), e);
        }
    }

    private void applyAuth(HttpHeaders headers) {
        String token = properties.getAuthToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
