package nl.metafactory.agentruntime.definition.guardrail;

import com.embabel.agent.api.validation.guardrails.GuardRail;

import java.util.List;
import java.util.Set;

/**
 * Code that really enforces one guardrail id. A guardrail id that appears only in a definition,
 * a prompt or a policy file without a matching, operational executor bean is not a guardrail.
 *
 * <p>Embabel 1.5.1 only aborts an LLM call for {@code CRITICAL} violations ({@code ERROR} is merely
 * logged) and de-duplicates guardrails by class, so executors must report blocking violations as
 * {@code CRITICAL} and use one class per guardrail.
 */
public interface GuardrailExecutor {

    String guardrailId();

    String executorName();

    Set<GuardrailScope> scopes();

    /** False when the backing enforcement (e.g. a remote policy engine) is disabled or down. */
    boolean operational();

    /**
     * Whether this guardrail is verifiably enforced for traffic to {@code modelAlias}. Executors
     * whose coverage depends on request selection (e.g. a LiteLLM policy attached to a model)
     * override this; in-process executors cover every model.
     */
    default boolean operationalFor(String modelAlias) {
        return operational();
    }

    /** Guardrails attached to every Embabel LLM interaction of a run that requires this id. */
    default List<GuardRail> embabelGuardRails() {
        return List.of();
    }
}
