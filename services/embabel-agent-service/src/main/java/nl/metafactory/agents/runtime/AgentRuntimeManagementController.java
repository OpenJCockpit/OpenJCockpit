package nl.metafactory.agents.runtime;

import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.common.agentruntime.ToolInvocationDecision;
import nl.metafactory.common.agentruntime.WorkflowRunDescriptor;
import nl.metafactory.common.security.ServiceCallers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Internal, service-to-service: the management data the agent runtime needs to run agents, owned here —
 * tenant bindings ({@code agent_tenant_bindings}), the scope of active workflow runs (this engine's
 * registry) and tool-call policy decisions (OPA, with this service's runtime-editable OPA configuration and
 * decision log). Only the runtime's identity ({@code azp}) may call it; every other caller gets 403.
 */
@RestController
public class AgentRuntimeManagementController {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeManagementController.class);

    private final AgentTenantBindingRepository bindings;
    private final WorkflowRunScopes runScopes;
    private final PolicyDecisionService policy;
    private final AgentRuntimeProperties properties;

    public AgentRuntimeManagementController(AgentTenantBindingRepository bindings, WorkflowRunScopes runScopes,
                                            PolicyDecisionService policy, AgentRuntimeProperties properties) {
        this.bindings = bindings;
        this.runScopes = runScopes;
        this.policy = policy;
        this.properties = properties;
    }

    @GetMapping(path = AgentRuntimeApi.TENANT_BINDING, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentTenantBinding> tenantBinding(@PathVariable String tenantId, @PathVariable String agentId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        if (!fromRuntime(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return bindings.find(tenantId, agentId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = AgentRuntimeApi.WORKFLOW_RUN, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkflowRunDescriptor> workflowRun(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        if (!fromRuntime(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return runScopes.find(runId).map(AgentRuntimeManagementController::descriptor).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = AgentRuntimeApi.TOOL_INVOCATION_DECISIONS, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ToolInvocationDecision> decide(@RequestBody ToolInvocationDecision.Request request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        if (!fromRuntime(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.tenantId() == null || request.agentId() == null || request.toolId() == null
                || request.workflowRunId() == null) {
            return ResponseEntity.badRequest().build();
        }
        // Same context the in-process authoriser built: tool calls of A2A-served/catalog agents.
        var context = new PolicyDecisionContext(request.workflowId(), request.workflowRunId(), null, null,
                request.tenantId(), null, null, "a2a", request.agentId(), null, null, null, null, null,
                request.toolId(), "invoke", "mcp.tool.invoke", request.principal(), null, null, null, null, null,
                null, Instant.now(), java.util.Map.<String, Object>copyOf(request.attributes()));
        var decision = policy.canInvokeMcpTool(context);
        return ResponseEntity.ok(new ToolInvocationDecision(decision.allowed(), decision.reason()));
    }

    static WorkflowRunDescriptor descriptor(WorkflowRunScope scope) {
        var ws = scope.gitWorkspace();
        return new WorkflowRunDescriptor(scope.workflowId(), scope.workflowRunId(), ws == null ? null
                : new WorkflowRunDescriptor.GitWorkspace(ws.workspaceKey(), ws.repositoryUrl(), ws.branch()));
    }

    private boolean fromRuntime(Jwt jwt) {
        if (ServiceCallers.is(jwt, properties.getRuntimeClientId())) {
            return true;
        }
        log.warn("agent-runtime.management.denied reason=caller-not-allowed caller={}", ServiceCallers.clientId(jwt));
        return false;
    }
}
