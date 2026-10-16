package nl.metafactory.agents.definition.guardrail;

import com.embabel.agent.api.validation.guardrails.GuardRail;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Binds required guardrail ids to operational executors, failing closed. Required ids are the
 * definition's own list plus the server-wide mandatory set; a definition can add guardrails but
 * never drop a mandatory one.
 */
public class GuardrailPolicyEnforcer {

    public record GuardrailBinding(List<String> guardrailIds, Map<String, String> executors, List<GuardRail> guardRails) {

        public GuardrailBinding {
            guardrailIds = List.copyOf(guardrailIds);
            executors = Map.copyOf(executors);
            guardRails = List.copyOf(guardRails);
        }
    }

    private final Map<String, GuardrailExecutor> executors;
    private final Set<String> mandatory;

    public GuardrailPolicyEnforcer(List<GuardrailExecutor> executors, Set<String> mandatory) {
        var map = new TreeMap<String, GuardrailExecutor>();
        for (GuardrailExecutor executor : executors) {
            if (map.putIfAbsent(executor.guardrailId(), executor) != null) {
                throw new IllegalStateException("duplicate guardrail executor for " + executor.guardrailId());
            }
        }
        this.executors = Map.copyOf(map);
        this.mandatory = Set.copyOf(mandatory);
    }

    public GuardrailBinding enforce(String agentId, List<String> requested) {
        return enforce(agentId, null, requested);
    }

    public GuardrailBinding enforce(String agentId, String modelAlias, List<String> requested) {
        var required = new LinkedHashSet<>(requested);
        required.addAll(new java.util.TreeSet<>(mandatory));
        var bound = new TreeMap<String, String>();
        var guardRails = new ArrayList<GuardRail>();
        for (String id : required) {
            var executor = executors.get(id);
            if (executor == null) {
                throw new AgentResolutionException(ResolutionFailure.GUARDRAIL_MISSING,
                        "agent " + agentId + " requires guardrail " + id + " but no executor enforces it");
            }
            if (modelAlias == null ? !executor.operational() : !executor.operationalFor(modelAlias)) {
                throw new AgentResolutionException(ResolutionFailure.GUARDRAIL_MISSING,
                        "agent " + agentId + " requires guardrail " + id + " but executor "
                                + executor.executorName() + " is not operational");
            }
            if (executor.scopes().isEmpty()) {
                throw new AgentResolutionException(ResolutionFailure.GUARDRAIL_MISSING,
                        "guardrail " + id + " executor covers no traffic");
            }
            bound.put(id, executor.executorName());
            guardRails.addAll(executor.embabelGuardRails());
        }
        return new GuardrailBinding(List.copyOf(required), bound, guardRails);
    }
}
