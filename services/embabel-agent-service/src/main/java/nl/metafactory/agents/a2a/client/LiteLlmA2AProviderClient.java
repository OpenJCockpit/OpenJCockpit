package nl.metafactory.agents.a2a.client;

import nl.metafactory.agents.a2a.client.AgentCardMetadata;
import nl.metafactory.agents.a2a.client.LiteLlmAgentMetadataClient;
import nl.metafactory.common.agentdefinition.LiteLlmHttpClient;
import nl.metafactory.common.agentdefinition.Identifiers;
import nl.metafactory.common.agentdefinition.SourceStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link A2AProviderClient} for the LiteLLM A2A Gateway. Routes verified in the pinned LiteLLM
 * v1.101.2 source: {@code GET /v1/agents} (registry), {@code GET /a2a/{name}/.well-known/agent-card.json}
 * (hosted card, url rewritten to the proxy), {@code POST /a2a/{name}} (JSON-RPC {@code message/send};
 * header {@code x-a2a-<name>-authorization} is forwarded to the agent as {@code authorization}).
 * LiteLLM shows a non-admin key only explicitly granted agents, so the configured key needs
 * {@code object_permission.agents} grants and the matching {@code allowed_routes}.
 */
public class LiteLlmA2AProviderClient implements A2AProviderClient {

    public static final String TYPE = "litellm";
    static final String CARD_PATH = "/.well-known/agent-card.json";

    private final A2AProvidersProperties.Provider config;
    private final LiteLlmHttpClient http;
    private final LiteLlmHttpClient messageHttp;
    private final LiteLlmAgentMetadataClient registry;
    private final ClientCredentialsTokens callerTokens;

    public LiteLlmA2AProviderClient(A2AProvidersProperties.Provider config) {
        this.config = config;
        boolean usable = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                && config.getApiKey() != null && !config.getApiKey().isBlank();
        this.http = usable ? new LiteLlmHttpClient(config.getBaseUrl(), config.getApiKey(),
                config.getConnectTimeout(), config.getReadTimeout()) : null;
        this.messageHttp = usable ? new LiteLlmHttpClient(config.getBaseUrl(), config.getApiKey(),
                config.getConnectTimeout(), config.getMessageTimeout()) : null;
        this.registry = usable ? new LiteLlmAgentMetadataClient(http) : null;
        this.callerTokens = new ClientCredentialsTokens(config.getCallerAuth(), java.time.Clock.systemUTC());
    }

    /** The provider's HTTP connection, for LiteLLM-specific management calls (e.g. policy checks). */
    public Optional<LiteLlmHttpClient> http() {
        return Optional.ofNullable(http);
    }

    @Override
    public String id() {
        return config.effectiveId();
    }

    @Override
    public String name() {
        return config.getName() == null || config.getName().isBlank() ? "LiteLLM" : config.getName();
    }

    @Override
    public boolean configured() {
        return http != null;
    }

    @Override
    public Optional<String> callerAuthorization() {
        return callerTokens.authorization();
    }

    @Override
    public Listing listAgents() {
        if (!configured()) {
            return new Listing(Status.NOT_CONFIGURED, java.util.List.of(), 0,
                    "Set base-url and api-key for A2A provider " + name());
        }
        var result = registry.listAll();
        if (result.status() != SourceStatus.FOUND) {
            return new Listing(map(result.status()), java.util.List.of(), 0, result.reason());
        }
        return new Listing(Status.OK, result.agents().stream().map(this::toAgent).toList(), result.skippedInvalid(), null);
    }

    @Override
    public CardResult agentCard(String agentName) {
        if (!configured()) {
            return new CardResult(Status.NOT_CONFIGURED, null);
        }
        if (!Identifiers.isSlug(agentName)) {
            return new CardResult(Status.NOT_FOUND, null);
        }
        var response = http.get("/a2a/{name}" + CARD_PATH, agentName);
        if (response.status() != SourceStatus.FOUND) {
            return new CardResult(map(response.status()), null);
        }
        if (!(response.body() instanceof ObjectNode card)) {
            return new CardResult(Status.INVALID, null);
        }
        String gateway = gatewayUrl(agentName);
        card.put("url", gateway);
        if (card.get("supportedInterfaces") instanceof ArrayNode interfaces && !interfaces.isEmpty()
                && interfaces.get(0) instanceof ObjectNode first) {
            first.put("url", gateway);
        }
        return new CardResult(Status.OK, card);
    }

    @Override
    public MessageResult sendMessage(String agentName, String text, String callerAuthorization) {
        return sendMessage(agentName, text, callerAuthorization, Map.of());
    }

    private static final java.util.regex.Pattern HEADER_NAME = java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    @Override
    public MessageResult sendMessage(String agentName, String text, String callerAuthorization,
                                     Map<String, String> forwardedHeaders) {
        if (!configured()) {
            return new MessageResult(Status.NOT_CONFIGURED, null, "provider not configured");
        }
        if (!Identifiers.isSlug(agentName) || text == null || text.isBlank()) {
            return new MessageResult(Status.INVALID, null, "invalid agent name or empty message");
        }
        var body = Map.of("jsonrpc", "2.0", "id", UUID.randomUUID().toString(), "method", "message/send",
                "params", Map.of("message", Map.of("kind", "message", "role", "user",
                        "messageId", UUID.randomUUID().toString(),
                        "parts", java.util.List.of(Map.of("kind", "text", "text", text)))));
        var headers = new java.util.LinkedHashMap<String, String>();
        if (callerAuthorization != null && !callerAuthorization.isBlank()) {
            headers.put("x-a2a-" + agentName + "-authorization", callerAuthorization);
        }
        if (forwardedHeaders != null) {
            for (var header : forwardedHeaders.entrySet()) {
                if (!HEADER_NAME.matcher(header.getKey()).matches() || "authorization".equals(header.getKey())) {
                    return new MessageResult(Status.INVALID, null, "invalid forwarded header");
                }
                // LiteLLM v1.101.2 forwards x-a2a-<agent_name>-<header> to the agent as <header>
                headers.put("x-a2a-" + agentName + "-" + header.getKey(), header.getValue());
            }
        }
        var response = messageHttp.post("/a2a/" + agentName, body, headers);
        if (response.status() != SourceStatus.FOUND) {
            return new MessageResult(map(response.status()), null, response.reason());
        }
        JsonNode rpc = response.body();
        if (rpc.get("error") instanceof JsonNode error && error.isObject()) {
            String reason = error.path("data").path("reason").asString("");
            return new MessageResult(Status.INVALID, null,
                    error.path("code").asInt() + " " + error.path("message").asString("") + (reason.isEmpty() ? "" : " (" + reason + ")"));
        }
        JsonNode result = rpc.path("result");
        // A2A 0.3: Message (kind=message) or Task with artifacts; 1.0: {"message": {...}}
        JsonNode message = result.has("message") ? result.get("message") : result;
        var out = new StringBuilder();
        appendText(message.path("parts"), out);
        for (JsonNode artifact : result.path("artifacts")) {
            appendText(artifact.path("parts"), out);
        }
        return out.isEmpty() ? new MessageResult(Status.INVALID, null, "reply contains no text")
                : new MessageResult(Status.OK, out.toString(), null);
    }

    private static void appendText(JsonNode parts, StringBuilder out) {
        for (JsonNode part : parts) {
            if (part.get("text") instanceof JsonNode t && t.isString()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(t.asString());
            }
        }
    }

    String gatewayUrl(String agentName) {
        return config.effectivePublicUrl() + "/a2a/" + agentName;
    }

    private Agent toAgent(AgentCardMetadata m) {
        String gateway = gatewayUrl(m.agentName());
        return new Agent(m.litellmAgentId(), m.agentName(), m.description(), gateway, gateway + CARD_PATH, m.url(),
                m.protocolVersion(), m.cardVersion(), m.streaming(),
                m.skills().stream().map(s -> s.name() != null ? s.name() : s.id()).toList());
    }

    private static Status map(SourceStatus status) {
        return switch (status) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case UNAUTHORIZED -> Status.UNAUTHORIZED;
            case INVALID -> Status.INVALID;
            default -> Status.UNAVAILABLE;
        };
    }
}
