package nl.metafactory.agents.a2a.client;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * The single, reusable client for one A2A provider (an A2A gateway/registry such as LiteLLM).
 * Everything in this service that talks to an A2A provider — the agent catalog, agent cards,
 * calling agents — goes through the provider's one client instance (see {@link A2AProviderClients}).
 */
public interface A2AProviderClient {

    /** Stable provider id used in URLs and configuration, e.g. {@code litellm}. */
    String id();

    /** Display name, e.g. {@code LiteLLM}. */
    String name();

    /** False when the provider is listed but not usable (missing URL or key). */
    boolean configured();

    /**
     * {@code Authorization} value of this service's caller identity at the provider (client credentials),
     * or empty when the provider has none. The provider forwards it to the agent.
     */
    default java.util.Optional<String> callerAuthorization() {
        return java.util.Optional.empty();
    }

    /** Every agent this client's credentials may see at the provider. */
    Listing listAgents();

    /** The agent card the provider hosts for {@code agentName}, with its url set to the public gateway. */
    CardResult agentCard(String agentName);

    /**
     * Sends one text message to {@code agentName} through the provider ({@code message/send}).
     *
     * @param callerAuthorization optional {@code Authorization} value for the agent behind the
     *                            provider (e.g. {@code Bearer <jwt>}); forwarded by the provider
     */
    MessageResult sendMessage(String agentName, String text, String callerAuthorization);

    /**
     * As {@link #sendMessage(String, String, String)}, with extra headers the provider forwards to the
     * agent (LiteLLM: {@code x-a2a-<agent>-<header>} arrives at the agent as {@code <header>}). Header names
     * are lower-case tokens; values must not contain secrets beyond what the agent may see.
     */
    default MessageResult sendMessage(String agentName, String text, String callerAuthorization,
                                      java.util.Map<String, String> forwardedHeaders) {
        if (forwardedHeaders != null && !forwardedHeaders.isEmpty()) {
            return new MessageResult(Status.INVALID, null, "this provider cannot forward headers to agents");
        }
        return sendMessage(agentName, text, callerAuthorization);
    }

    /** Status of a provider call; mirrors the catalog's section status values. */
    enum Status { OK, NOT_CONFIGURED, NOT_FOUND, UNAVAILABLE, UNAUTHORIZED, INVALID }

    /** One agent as listed by the provider. {@code url} is the public gateway endpoint. */
    record Agent(String id, String name, String description, String url, String cardUrl, String backendUrl,
                 String protocolVersion, String version, boolean streaming, List<String> skills) {

        public Agent {
            skills = List.copyOf(skills);
        }
    }

    record Listing(Status status, List<Agent> agents, int skippedInvalid, String reason) {

        public Listing {
            agents = List.copyOf(agents);
        }
    }

    record CardResult(Status status, JsonNode card) {
    }

    /** {@code text} is the concatenated text parts of the agent's reply; {@code error} a safe summary. */
    record MessageResult(Status status, String text, String error) {
    }
}
