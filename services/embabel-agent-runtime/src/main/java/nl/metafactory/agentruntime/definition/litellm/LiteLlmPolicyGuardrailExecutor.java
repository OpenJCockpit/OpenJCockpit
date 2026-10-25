package nl.metafactory.agentruntime.definition.litellm;

import nl.metafactory.common.agentdefinition.LiteLlmHttpClient;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailExecutor;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailScope;
import nl.metafactory.common.agentdefinition.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A guardrail enforced by the LiteLLM model gateway. It only counts as operational for a model
 * alias when LiteLLM itself confirms — via {@code POST /policies/resolve} (verified in LiteLLM
 * v1.101.2 {@code proxy/policy_engine/policy_resolve_endpoints.py}, response
 * {@code effective_guardrails}) — that the named LiteLLM guardrail is applied to requests for that
 * model. Any error, auth failure or absence means not operational, so the run is blocked.
 *
 * <p>Scope is limited to the LLM calls this service makes through the gateway; it says nothing
 * about A2A traffic or tool calls, which are covered by local executors.
 */
public class LiteLlmPolicyGuardrailExecutor implements GuardrailExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiteLlmPolicyGuardrailExecutor.class);
    static final Duration VERIFIED_TTL = Duration.ofSeconds(60);

    private final String guardrailId;
    private final String litellmGuardrailName;
    private final LiteLlmHttpClient http;
    private final Clock clock;
    private final Map<String, Instant> verifiedUntil = new ConcurrentHashMap<>();

    public LiteLlmPolicyGuardrailExecutor(String guardrailId, String litellmGuardrailName, LiteLlmHttpClient http,
                                          Clock clock) {
        this.guardrailId = guardrailId;
        this.litellmGuardrailName = litellmGuardrailName;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public String guardrailId() {
        return guardrailId;
    }

    @Override
    public String executorName() {
        return "litellm-policy:" + litellmGuardrailName;
    }

    @Override
    public Set<GuardrailScope> scopes() {
        return Set.of(GuardrailScope.LLM_INPUT, GuardrailScope.LLM_OUTPUT);
    }

    /** Without a model alias coverage cannot be proven. */
    @Override
    public boolean operational() {
        return false;
    }

    @Override
    public boolean operationalFor(String modelAlias) {
        if (modelAlias == null) {
            return false;
        }
        Instant until = verifiedUntil.get(modelAlias);
        if (until != null && clock.instant().isBefore(until)) {
            return true;
        }
        var response = http.post("/policies/resolve", Map.of("model", modelAlias));
        boolean applied = response.status() == SourceStatus.FOUND && contains(response.body(), litellmGuardrailName);
        if (applied) {
            verifiedUntil.put(modelAlias, clock.instant().plus(VERIFIED_TTL));
        } else {
            verifiedUntil.remove(modelAlias);
            log.warn("guardrail.gateway.unverified guardrail={} litellmGuardrail={} model={} status={}", guardrailId,
                    litellmGuardrailName, modelAlias, response.status());
        }
        return applied;
    }

    private static boolean contains(JsonNode body, String name) {
        JsonNode effective = body == null ? null : body.get("effective_guardrails");
        if (effective == null || !effective.isArray()) {
            return false;
        }
        for (JsonNode n : effective) {
            if (n.isString() && name.equals(n.asString())) {
                return true;
            }
        }
        return false;
    }
}
