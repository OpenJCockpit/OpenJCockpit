package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.model.SourceStatus;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads agent metadata from LiteLLM's A2A Agent Registry using the routes verified in the pinned
 * LiteLLM v1.101.2 source ({@code litellm/proxy/agent_endpoints/endpoints.py}):
 * {@code GET /v1/agents} → {@code list[AgentResponse]} with {@code agent_id}, {@code agent_name}
 * and {@code agent_card_params} (an A2A AgentCard). Agents are matched on {@code agent_name}
 * equal to our agent id. Returns metadata only — never prompt content.
 */
public class LiteLlmAgentMetadataClient {

    public record Result(SourceStatus status, AgentCardMetadata metadata, String reason) {

        static Result of(SourceStatus status, String reason) {
            return new Result(status, null, reason);
        }
    }

    private final LiteLlmHttpClient http;

    public LiteLlmAgentMetadataClient(LiteLlmHttpClient http) {
        this.http = http;
    }

    /** Every registry agent visible to the configured key, in registry order. */
    public record ListResult(SourceStatus status, List<AgentCardMetadata> agents, int skippedInvalid, String reason) {
    }

    public ListResult listAll() {
        var response = http.get("/v1/agents");
        if (response.status() != SourceStatus.FOUND) {
            return new ListResult(response.status(), List.of(), 0, response.reason());
        }
        if (!response.body().isArray()) {
            return new ListResult(SourceStatus.INVALID, List.of(), 0, "agent list is not an array");
        }
        var agents = new ArrayList<AgentCardMetadata>();
        int skipped = 0;
        for (JsonNode agent : response.body()) {
            var parsed = parse(agent);
            if (parsed.status() == SourceStatus.FOUND) {
                agents.add(parsed.metadata());
            } else {
                skipped++;
            }
        }
        return new ListResult(SourceStatus.FOUND, List.copyOf(agents), skipped, null);
    }

    public Result findByName(String agentName) {
        var response = http.get("/v1/agents");
        if (response.status() != SourceStatus.FOUND) {
            return Result.of(response.status(), response.reason());
        }
        if (!response.body().isArray()) {
            return Result.of(SourceStatus.INVALID, "agent list is not an array");
        }
        var matches = new ArrayList<JsonNode>();
        for (JsonNode agent : response.body()) {
            if (agentName.equals(text(agent, "agent_name"))) {
                matches.add(agent);
            }
        }
        if (matches.isEmpty()) {
            return Result.of(SourceStatus.NOT_FOUND, "no registry agent named " + agentName);
        }
        if (matches.size() > 1) {
            return Result.of(SourceStatus.INVALID, "registry agent name is ambiguous");
        }
        return parse(matches.getFirst());
    }

    static Result parse(JsonNode agent) {
        String id = text(agent, "agent_id");
        String name = text(agent, "agent_name");
        JsonNode card = agent.get("agent_card_params");
        if (id == null || name == null || card == null || !card.isObject()) {
            return Result.of(SourceStatus.INVALID, "agent payload lacks agent_id, agent_name or agent_card_params");
        }
        String protocol = text(card, "protocolVersion");
        String url = text(card, "url");
        if (url == null || protocol == null) {
            return Result.of(SourceStatus.INVALID, "agent card lacks url or protocolVersion");
        }
        var skills = new ArrayList<AgentCardMetadata.CardSkill>();
        JsonNode cardSkills = card.get("skills");
        if (cardSkills != null && !cardSkills.isNull()) {
            if (!cardSkills.isArray()) {
                return Result.of(SourceStatus.INVALID, "agent card skills is not an array");
            }
            for (JsonNode s : cardSkills) {
                if (text(s, "id") == null) {
                    return Result.of(SourceStatus.INVALID, "agent card skill without id");
                }
                skills.add(new AgentCardMetadata.CardSkill(text(s, "id"), text(s, "name"), text(s, "description")));
            }
        }
        JsonNode caps = card.get("capabilities");
        boolean streaming = caps != null && caps.path("streaming").asBoolean(false);
        return new Result(SourceStatus.FOUND,
                new AgentCardMetadata(id, name, text(card, "description"), protocol, url, text(card, "version"), streaming, List.copyOf(skills)), null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || !v.isString() || v.asString().isBlank() ? null : v.asString();
    }
}
