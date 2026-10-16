package nl.metafactory.agents.a2a;

import nl.metafactory.agents.gitworkspace.GitWorkspaceProperties;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;

import com.embabel.agent.api.validation.guardrails.GuardRailViolationException;
import nl.metafactory.agents.definition.config.AgentDefinitionProperties;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound A2A (0.3, JSON-RPC) endpoint for configuration-driven agents, intended to be called via
 * the LiteLLM A2A Gateway. Authentication is the service's existing JWT resource server; the
 * tenant comes from the configured JWT claim. Each {@code message/send} becomes exactly one
 * authorised {@link AgentRunService} run. Message text, prompts and tool data are never logged.
 */
@RestController
@ConditionalOnProperty(name = "openjcockpit.agent-definitions.a2a.enabled", havingValue = "true")
public class A2AInboundAdapter {

    private static final Logger log = LoggerFactory.getLogger(A2AInboundAdapter.class);

    private final AgentRunService runs;
    private final AgentDefinitionProperties properties;
    private final WorkflowRunScopes runScopes;
    private final GitWorkspaceProperties gitWorkspaceProperties;
    private final JsonMapper json = JsonMapper.builder().build();

    public A2AInboundAdapter(AgentRunService runs, AgentDefinitionProperties properties) {
        this(runs, properties, new WorkflowRunScopes(), new GitWorkspaceProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public A2AInboundAdapter(AgentRunService runs, AgentDefinitionProperties properties,
                             WorkflowRunScopes runScopes, GitWorkspaceProperties gitWorkspaceProperties) {
        this.runs = runs;
        this.properties = properties;
        this.runScopes = runScopes;
        this.gitWorkspaceProperties = gitWorkspaceProperties;
    }

    @PostMapping(path = "/a2a/{agentId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode handle(@PathVariable String agentId, @RequestBody String body,
                           @AuthenticationPrincipal Jwt jwt,
                           @RequestHeader(name = "traceparent", required = false) String traceparent,
                           @RequestHeader(name = WorkflowRunScopes.RUN_HEADER, required = false) String workflowRunId) {
        JsonNode request;
        try {
            request = json.readTree(body);
        } catch (JacksonException e) {
            return error(null, A2AProtocol.PARSE_ERROR, "Parse error", null);
        }
        JsonNode id = request == null ? null : request.get("id");
        if (request == null || !request.isObject() || !"2.0".equals(text(request, "jsonrpc"))
                || !(request.get("method") instanceof JsonNode m && m.isString())
                || (id != null && !(id.isString() || id.isIntegralNumber() || id.isNull()))) {
            return error(id, A2AProtocol.INVALID_REQUEST, "Invalid Request", null);
        }
        String method = request.get("method").asString();
        return switch (method) {
            case A2AProtocol.MESSAGE_SEND -> send(agentId, id, request.get("params"), jwt, traceparent,
                    workflowOf(workflowRunId, jwt, agentId));
            case A2AProtocol.TASKS_GET, A2AProtocol.TASKS_CANCEL ->
                    error(id, A2AProtocol.TASK_NOT_FOUND, "Task not found: this agent does not create tasks", null);
            case A2AProtocol.MESSAGE_STREAM, "tasks/resubscribe", "tasks/pushNotificationConfig/set",
                 "tasks/pushNotificationConfig/get", "tasks/pushNotificationConfig/list",
                 "tasks/pushNotificationConfig/delete", "agent/getAuthenticatedExtendedCard" ->
                    error(id, A2AProtocol.UNSUPPORTED_OPERATION, "This operation is not supported", null);
            default -> error(id, A2AProtocol.METHOD_NOT_FOUND, "Method not found", null);
        };
    }

    /**
     * The calling workflow run (workflow, run id, optional git workspace): only for the engine's own A2A
     * identity and only for a run this instance is executing. Anything else runs the agent outside a
     * workflow, so its tool calls are not attributed (and refused by the policy).
     */
    private WorkflowRunScope workflowOf(String workflowRunId, Jwt jwt, String agentId) {
        if (workflowRunId == null || workflowRunId.isBlank()) {
            return null;
        }
        String caller = jwt == null ? null : jwt.getClaimAsString("azp");
        if (caller == null || !caller.equals(gitWorkspaceProperties.getEngineA2aClientId())) {
            log.warn("a2a.workflow-run.ignored agentId={} reason=caller-not-engine", agentId);
            return null;
        }
        return runScopes.find(workflowRunId).orElse(null);
    }

    private JsonNode send(String agentId, JsonNode id, JsonNode params, Jwt jwt, String traceparent,
                          WorkflowRunScope workflow) {
        JsonNode message = params == null ? null : params.get("message");
        if (message == null || !message.isObject() || !"user".equals(text(message, "role"))
                || !(message.get("parts") instanceof JsonNode parts) || !parts.isArray() || parts.isEmpty()) {
            return error(id, A2AProtocol.INVALID_PARAMS, "params.message must be a user message with parts", null);
        }
        var text = new StringBuilder();
        for (JsonNode part : message.get("parts")) {
            if (!"text".equals(text(part, "kind")) || text(part, "text") == null) {
                return error(id, A2AProtocol.CONTENT_TYPE_NOT_SUPPORTED, "Only text parts are supported", null);
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(text(part, "text"));
        }
        if (text.length() > A2AProtocol.MAX_MESSAGE_CHARS) {
            return error(id, A2AProtocol.INVALID_PARAMS, "Message too long", null);
        }
        String tenant = jwt == null ? null : jwt.getClaimAsString(properties.getTenantClaim());
        if (tenant == null || tenant.isBlank()) {
            log.warn("a2a.request.rejected agentId={} reason=missing-tenant-claim", agentId);
            return error(id, A2AProtocol.INVALID_REQUEST, "Request not permitted", Map.of("reason", "TENANT_FORBIDDEN"));
        }
        String principal = jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username") : jwt.getSubject();
        try {
            var response = runs.run(tenant, agentId, principal == null ? "unknown" : principal, traceparent, text.toString(),
                    workflow);
            ObjectNode result = json.createObjectNode();
            result.put("kind", "message");
            result.put("role", "agent");
            result.put("messageId", UUID.randomUUID().toString());
            if (text(message, "contextId") != null) {
                result.put("contextId", text(message, "contextId"));
            }
            var part = result.putArray("parts").addObject();
            part.put("kind", "text");
            part.put("text", response.text());
            var meta = result.putObject("metadata");
            meta.put("agentId", response.agentId());
            meta.put("agentVersion", response.version());
            meta.put("contentHash", response.contentHash());
            meta.put("traceId", response.traceId());
            return envelope(id).set("result", result);
        } catch (AgentResolutionException e) {
            boolean transientFailure = switch (e.failure()) {
                case UNAVAILABLE -> true;
                default -> false;
            };
            return error(id, transientFailure ? A2AProtocol.INTERNAL_ERROR : A2AProtocol.INVALID_REQUEST,
                    transientFailure ? "Agent definition temporarily unavailable" : "Request not permitted",
                    Map.of("reason", e.failure().name()));
        } catch (GuardRailViolationException e) {
            log.warn("a2a.request.blocked agentId={} guard={}", agentId, e.getGuard());
            return error(id, A2AProtocol.INVALID_REQUEST, "Request blocked by guardrail",
                    Map.of("reason", "GUARDRAIL_VIOLATION"));
        } catch (RuntimeException e) {
            log.error("a2a.request.failed agentId={} error={}", agentId, e.getClass().getSimpleName());
            return error(id, A2AProtocol.INTERNAL_ERROR, "Internal error", null);
        }
    }

    private ObjectNode envelope(JsonNode id) {
        ObjectNode node = json.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (id == null) {
            node.putNull("id");
        } else {
            node.set("id", id);
        }
        return node;
    }

    private JsonNode error(JsonNode id, int code, String message, Map<String, String> data) {
        ObjectNode node = envelope(id);
        ObjectNode err = node.putObject("error");
        err.put("code", code);
        err.put("message", message);
        if (data != null) {
            var d = err.putObject("data");
            data.forEach(d::put);
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v != null && v.isString() ? v.asString() : null;
    }
}
