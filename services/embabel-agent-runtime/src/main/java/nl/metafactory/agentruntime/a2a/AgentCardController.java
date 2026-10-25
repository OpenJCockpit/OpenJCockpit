package nl.metafactory.agentruntime.a2a;

import nl.metafactory.agentruntime.definition.config.AgentDefinitionProperties;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.resolver.AgentDefinitionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Dynamic A2A 0.3 Agent Card per agent and tenant. The card is only produced after a full,
 * successful resolution for the caller's tenant (binding, tools, guardrails, model), so it never
 * advertises an agent the caller cannot run. It lists exactly one A2A skill — the agent itself —
 * and never the internal Embabel skill packages, tools or guardrails. Capabilities state what is
 * implemented: no streaming, no push notifications, no task history.
 */
@RestController
@ConditionalOnProperty(name = "openjcockpit.agent-definitions.a2a.enabled", havingValue = "true")
public class AgentCardController {

    private static final Logger log = LoggerFactory.getLogger(AgentCardController.class);

    private final AgentDefinitionResolver resolver;
    private final AgentDefinitionProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    public AgentCardController(AgentDefinitionResolver resolver, AgentDefinitionProperties properties) {
        this.resolver = resolver;
        this.properties = properties;
    }

    @GetMapping(path = {"/a2a/{agentId}/.well-known/agent-card.json", "/a2a/{agentId}/.well-known/agent.json"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> card(@PathVariable String agentId, @AuthenticationPrincipal Jwt jwt) {
        String tenant = jwt == null ? null : jwt.getClaimAsString(properties.getTenantClaim());
        try {
            var prepared = resolver.resolve(tenant, agentId);
            return ResponseEntity.ok(build(prepared.definition().agentId(), prepared.definition().version(),
                    prepared.description()));
        } catch (AgentResolutionException e) {
            // 404 for every refusal: the card must not reveal whether an agent exists for others.
            log.info("a2a.card.refused agentId={} failure={}", agentId, e.failure());
            return ResponseEntity.notFound().build();
        }
    }

    JsonNode build(String agentId, String version, String description) {
        String base = properties.getA2a().getPublicBaseUrl().replaceAll("/+$", "");
        String text = description == null || description.isBlank() ? "Configuration-driven agent " + agentId : description;
        ObjectNode card = json.createObjectNode();
        card.put("protocolVersion", A2AProtocol.PROTOCOL_VERSION);
        card.put("name", agentId);
        card.put("description", text);
        card.put("url", base + "/a2a/" + agentId);
        card.put("preferredTransport", A2AProtocol.TRANSPORT);
        card.put("version", version);
        var caps = card.putObject("capabilities");
        caps.put("streaming", false);
        caps.put("pushNotifications", false);
        caps.put("stateTransitionHistory", false);
        card.putArray("defaultInputModes").add("text/plain");
        card.putArray("defaultOutputModes").add("text/plain");
        var skill = card.putArray("skills").addObject();
        skill.put("id", agentId);
        skill.put("name", agentId);
        skill.put("description", text);
        skill.putArray("tags").add("configuration-driven");
        skill.putArray("inputModes").add("text/plain");
        skill.putArray("outputModes").add("text/plain");
        var schemes = card.putObject("securitySchemes").putObject("bearer");
        schemes.put("type", "http");
        schemes.put("scheme", "bearer");
        schemes.put("bearerFormat", "JWT");
        card.putArray("security").addObject().putArray("bearer");
        card.put("supportsAuthenticatedExtendedCard", false);
        return card;
    }
}
