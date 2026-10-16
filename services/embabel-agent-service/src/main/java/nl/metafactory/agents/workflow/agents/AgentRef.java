package nl.metafactory.agents.workflow.agents;

import nl.metafactory.agents.definition.markdown.Identifiers;

import java.util.Optional;

/**
 * A workflow's reference to an agent in the central agent catalog (Workflow design → Agents):
 * {@code local:<agent-id>} for the repository {@code /agents} folder, or
 * {@code a2a:<provider>:<agent-name>} for an agent of an A2A provider (e.g. {@code a2a:litellm:qa-engineer}).
 * The key is what workflows store in {@code agentIds} and what approval gates and workflow orbs anchor to.
 */
public record AgentRef(Source source, String provider, String agentId) {

    public enum Source { LOCAL, A2A }

    public AgentRef {
        if (source == null || !Identifiers.isSlug(agentId)) {
            throw new IllegalArgumentException("invalid agent reference");
        }
        if ((source == Source.A2A) != (provider != null)) {
            throw new IllegalArgumentException("only A2A references carry a provider");
        }
        if (provider != null && !Identifiers.isSlug(provider)) {
            throw new IllegalArgumentException("invalid A2A provider id");
        }
    }

    public static AgentRef local(String agentId) {
        return new AgentRef(Source.LOCAL, null, agentId);
    }

    public static AgentRef a2a(String provider, String agentId) {
        return new AgentRef(Source.A2A, provider, agentId);
    }

    public String key() {
        return source == Source.LOCAL ? "local:" + agentId : "a2a:" + provider + ":" + agentId;
    }

    public static Optional<AgentRef> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String[] parts = key.split(":", -1);
        try {
            if (parts.length == 2 && parts[0].equals("local")) {
                return Optional.of(local(parts[1]));
            }
            if (parts.length == 3 && parts[0].equals("a2a")) {
                return Optional.of(a2a(parts[1], parts[2]));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static boolean isRef(String key) {
        return parse(key).isPresent();
    }

    @Override
    public String toString() {
        return key();
    }
}
