package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.model.OpaDecisionRequest;
import nl.metafactory.agents.policy.model.OpaDecisionResponse;

/**
 * Handles OPA communication only — no workflow-specific business logic. See PolicyDecisionService
 * for the layer that decides whether/how to call this client and what to do with the result.
 */
public interface OpenPolicyAgentClient {

    boolean isReachable();

    OpaDecisionResponse evaluate(OpaDecisionRequest request);
}
