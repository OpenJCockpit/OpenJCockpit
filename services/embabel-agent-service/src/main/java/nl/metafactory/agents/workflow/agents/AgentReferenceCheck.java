package nl.metafactory.agents.workflow.agents;

import java.util.Optional;

/** Checks that an agent reference points at an agent that exists in the central agent catalog. */
@FunctionalInterface
public interface AgentReferenceCheck {

    /** Empty when {@code ref} exists (or cannot be verified right now); otherwise a user-facing problem. */
    Optional<String> problem(AgentRef ref);
}
