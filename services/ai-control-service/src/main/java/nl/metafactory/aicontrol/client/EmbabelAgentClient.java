package nl.metafactory.aicontrol.client;

import nl.metafactory.common.policy.PolicyDecisionAuditEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class EmbabelAgentClient {

    private final WebClient webClient;

    public EmbabelAgentClient(WebClient embabelWebClient) {
        this.webClient = embabelWebClient;
    }

    public Optional<AgentRunDto> getLatestRun(String runId) {
        try {
            var run = webClient.get()
                    .uri("/api/agent-runs/{runId}", runId)
                    .retrieve()
                    .bodyToMono(AgentRunDto.class)
                    .block();
            return Optional.ofNullable(run);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Full output of each catalog agent of a workflow run, in execution order. */
    public List<AgentRunOutputDto> getAgentRunOutputs(String runId) {
        var outputs = webClient.get().uri("/api/agent-runs/{runId}/outputs", runId).retrieve()
                .bodyToFlux(AgentRunOutputDto.class).collectList().block();
        return outputs != null ? outputs : List.of();
    }

    // ── Calls made with an explicit service identity (git-workspace jobs run asynchronously,
    //    without a user token to relay) ──────────────────────────────────────────

    /** Starts a workflow as the given service identity ({@code Bearer <token>}). */
    public WorkflowStartResponseDto startWorkflowAs(String authorization, String id, WorkflowStartInputDto input) {
        try {
            return webClient.post().uri("/api/workflows/{id}/start", id)
                    .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                    .bodyValue(input != null ? input : WorkflowStartInputDto.empty()).retrieve()
                    .bodyToMono(WorkflowStartResponseDto.class).block();
        } catch (WebClientResponseException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + id);
        }
    }

    public Optional<AgentRunDto> getLatestRunAs(String authorization, String runId) {
        try {
            return Optional.ofNullable(webClient.get().uri("/api/agent-runs/{runId}", runId)
                    .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve().bodyToMono(AgentRunDto.class).block());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<AgentRunOutputDto> getAgentRunOutputsAs(String authorization, String runId) {
        var outputs = webClient.get().uri("/api/agent-runs/{runId}/outputs", runId)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                .retrieve().bodyToFlux(AgentRunOutputDto.class).collectList().block();
        return outputs != null ? outputs : List.of();
    }

    /** Request body of embabel's {@code POST /internal/git-workspace-tickets}. */
    public record GitWorkspaceTicketRequest(String workspaceKey, String repositoryUrl, String branch, String workflowId,
                                            String subject) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitWorkspaceTicketResponse(String ticket, Instant expiresAt) {
        @Override
        public String toString() {
            return "GitWorkspaceTicketResponse[ticket=***, expiresAt=" + expiresAt + "]";
        }
    }

    /**
     * Has embabel issue a single-use ticket (as ai-control's service identity) that binds one start of
     * {@code workflowId} by {@code subject} (null: ai-control itself) to a prepared git workspace.
     */
    public String issueGitWorkspaceTicketAs(String authorization, GitWorkspaceTicketRequest request) {
        var response = webClient.post().uri("/internal/git-workspace-tickets")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                .bodyValue(request).retrieve()
                .bodyToMono(GitWorkspaceTicketResponse.class).block();
        if (response == null || response.ticket() == null || response.ticket().isBlank()) {
            throw new IllegalStateException("embabel issued no git workspace ticket");
        }
        return response.ticket();
    }

    /** Stops a run as the given service identity (a timed-out git-workspace job). */
    public void stopAgentRunAs(String authorization, String runId) {
        webClient.delete().uri("/api/agent-runs/{runId}", runId)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                .retrieve().toBodilessEntity().block();
    }

    public void stopAgentRun(String runId) {
        webClient.delete()
                .uri("/api/agent-runs/{runId}", runId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    // ── Workflows ────────────────────────────────────────────────────────────

    public List<WorkflowDefinitionDto> listWorkflows() {
        // BR-13: deliberately does NOT swallow into List.of() — the dashboard must be able to
        // distinguish "the list failed to load" from "you have zero workflows". Mirrors the
        // listSkillSpecsOrThrow() precedent's intent, with the badRequest()/conflict()/notFound()
        // helper shape so the upstream message actually reaches the browser (AC-19).
        try {
            var workflows = webClient.get().uri("/api/workflows").retrieve()
                    .bodyToFlux(WorkflowDefinitionDto.class).collectList().block();
            return workflows != null ? workflows : List.of();
        } catch (WebClientResponseException e) {
            throw upstreamUnavailable(extractMessage(e, WORKFLOWS_UNAVAILABLE));
        } catch (WebClientRequestException e) {
            // Transport failure: e.getMessage() embeds the upstream host and port. Never relay it.
            throw upstreamUnavailable(WORKFLOWS_UNAVAILABLE);
        }
    }

    private static final String WORKFLOWS_UNAVAILABLE =
            "Workflows could not be loaded from the agent service";

    // Mirrors badRequest(...)/conflict(...)/notFound(...). 502 is the honest status for an
    // upstream failure and matches ProjectSpecController's existing BAD_GATEWAY use.
    private ResponseStatusException upstreamUnavailable(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    public Optional<WorkflowDefinitionDto> getWorkflow(String id) {
        try {
            var workflow = webClient.get().uri("/api/workflows/{id}", id).retrieve()
                    .bodyToMono(WorkflowDefinitionDto.class).block();
            return Optional.ofNullable(workflow);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public WorkflowDefinitionDto createWorkflow(WorkflowDefinitionDto request) {
        try {
            return webClient.post().uri("/api/workflows").bodyValue(request).retrieve()
                    .bodyToMono(WorkflowDefinitionDto.class).block();
        } catch (WebClientResponseException.BadRequest e) {
            throw badRequest(e);
        }
    }

    public WorkflowDefinitionDto updateWorkflow(String id, WorkflowDefinitionDto request) {
        try {
            return webClient.put().uri("/api/workflows/{id}", id).bodyValue(request).retrieve()
                    .bodyToMono(WorkflowDefinitionDto.class).block();
        } catch (WebClientResponseException.BadRequest e) {
            throw badRequest(e);
        }
    }

    // Validation errors from the embabel service (e.g. a workflow without both a group and a project)
    // must reach the dashboard as a 400 with the original message, not as a 500.
    private ResponseStatusException badRequest(WebClientResponseException.BadRequest e) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(e, "Bad Request"));
    }

    // D-QA-1 (workflow-approval-gate QA report §9): embabel-agent-service's error body is the
    // standard Spring Boot shape {"timestamp",...,"message",...,"path"} once
    // server.error.include-message is honoured there. Relaying e.getResponseBodyAsString()
    // verbatim as this exception's own reason would surface that whole JSON blob — not the clean
    // message text — as ai-control-service's own "message" field, which the dashboard renders
    // to the user as-is. Extract just the upstream "message" value, falling back to a generic
    // reason when the body isn't JSON or carries no (non-blank) message.
    private String extractMessage(WebClientResponseException e, String fallback) {
        try {
            EmbabelErrorBody body = e.getResponseBodyAs(EmbabelErrorBody.class);
            if (body != null && body.message() != null && !body.message().isBlank()) {
                return body.message();
            }
        } catch (Exception ignored) {
            // response body wasn't JSON (or had no "message" field) — fall back below
        }
        return fallback;
    }

    // The upstream body also carries timestamp/status/error/path (the standard Spring Boot error
    // shape) — @JsonIgnoreProperties(ignoreUnknown = true) is required so decoding this record
    // doesn't fail on those, which the default ObjectMapper otherwise rejects.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbabelErrorBody(String message) {}

    public void deleteWorkflow(String id) {
        try {
            webClient.delete().uri("/api/workflows/{id}", id).retrieve().toBodilessEntity().block();
        } catch (WebClientResponseException.Conflict e) {
            throw conflict(e);
        }
    }

    public WorkflowExportBundleDto exportWorkflows() {
        return webClient.get().uri("/api/workflows/export").retrieve()
                .bodyToMono(WorkflowExportBundleDto.class).block();
    }

    public WorkflowImportResultDto importWorkflows(WorkflowExportBundleDto bundle) {
        try {
            return webClient.post().uri("/api/workflows/import").bodyValue(bundle).retrieve()
                    .bodyToMono(WorkflowImportResultDto.class).block();
        } catch (WebClientResponseException.BadRequest e) {
            throw badRequest(e);
        }
    }

    // ── Workflow groups ──────────────────────────────────────────────────────

    public List<WorkflowGroupDto> listWorkflowGroups() {
        try {
            var groups = webClient.get().uri("/api/workflow-groups").retrieve()
                    .bodyToFlux(WorkflowGroupDto.class).collectList().block();
            return groups != null ? groups : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<WorkflowGroupDto> getWorkflowGroup(String id) {
        try {
            var group = webClient.get().uri("/api/workflow-groups/{id}", id).retrieve()
                    .bodyToMono(WorkflowGroupDto.class).block();
            return Optional.ofNullable(group);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public WorkflowGroupDto createWorkflowGroup(WorkflowGroupDto request) {
        return webClient.post().uri("/api/workflow-groups").bodyValue(request).retrieve()
                .bodyToMono(WorkflowGroupDto.class).block();
    }

    public WorkflowGroupDto updateWorkflowGroup(String id, WorkflowGroupDto request) {
        return webClient.put().uri("/api/workflow-groups/{id}", id).bodyValue(request).retrieve()
                .bodyToMono(WorkflowGroupDto.class).block();
    }

    public void deleteWorkflowGroup(String id) {
        webClient.delete().uri("/api/workflow-groups/{id}", id).retrieve().toBodilessEntity().block();
    }

    public WorkflowStartResponseDto startWorkflow(String id) {
        return startWorkflow(id, WorkflowStartInputDto.empty());
    }

    public WorkflowStartResponseDto startWorkflow(String id, WorkflowStartInputDto input) {
        try {
            return webClient.post().uri("/api/workflows/{id}/start", id)
                    .bodyValue(input != null ? input : WorkflowStartInputDto.empty()).retrieve()
                    .bodyToMono(WorkflowStartResponseDto.class).block();
        } catch (WebClientResponseException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + id);
        }
    }

    public List<PolicyDecisionAuditEntry> getDecisionLogsForWorkflow(String workflowId, Instant from, Instant to,
                                                                         String agentId, String subagentId, String skillId,
                                                                         String mcpToolName, String result) {
        try {
            var entries = webClient.get()
                    .uri(uriBuilder -> decisionLogUri(uriBuilder, "/api/workflows/{id}/decision-logs", from, to,
                            agentId, subagentId, skillId, mcpToolName, result, workflowId))
                    .retrieve()
                    .bodyToFlux(PolicyDecisionAuditEntry.class).collectList().block();
            return entries != null ? entries : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<PolicyDecisionAuditEntry> getDecisionLogsForWorkflowExecution(String workflowExecutionId, Instant from, Instant to,
                                                                                  String agentId, String subagentId, String skillId,
                                                                                  String mcpToolName, String result) {
        try {
            var entries = webClient.get()
                    .uri(uriBuilder -> decisionLogUri(uriBuilder, "/api/workflow-executions/{id}/decision-logs", from, to,
                            agentId, subagentId, skillId, mcpToolName, result, workflowExecutionId))
                    .retrieve()
                    .bodyToFlux(PolicyDecisionAuditEntry.class).collectList().block();
            return entries != null ? entries : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static java.net.URI decisionLogUri(org.springframework.web.util.UriBuilder uriBuilder, String path,
                                                Instant from, Instant to, String agentId, String subagentId,
                                                String skillId, String mcpToolName, String result, String id) {
        uriBuilder.path(path);
        if (from != null) uriBuilder.queryParam("from", from);
        if (to != null) uriBuilder.queryParam("to", to);
        if (agentId != null) uriBuilder.queryParam("agentId", agentId);
        if (subagentId != null) uriBuilder.queryParam("subagentId", subagentId);
        if (skillId != null) uriBuilder.queryParam("skillId", skillId);
        if (mcpToolName != null) uriBuilder.queryParam("mcpToolName", mcpToolName);
        if (result != null) uriBuilder.queryParam("result", result);
        return uriBuilder.build(id);
    }

    // ── OPA configuration ────────────────────────────────────────────────────

    public OpaConfigDto getOpaConfig() {
        return webClient.get().uri("/api/opa-config").retrieve()
                .bodyToMono(OpaConfigDto.class).block();
    }

    public OpaConfigDto saveOpaConfig(OpaConfigRequestDto request) {
        return webClient.put().uri("/api/opa-config").bodyValue(request).retrieve()
                .bodyToMono(OpaConfigDto.class).block();
    }

    public boolean checkOpaHealth() {
        try {
            var health = webClient.get().uri("/api/opa-config/health").retrieve()
                    .bodyToMono(java.util.Map.class).block();
            return health != null && Boolean.TRUE.equals(health.get("reachable"));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Agent catalog ────────────────────────────────────────────────────────

    /** Empty when embabel-agent-service cannot be reached; provider failures arrive as section status. */
    public Optional<AgentCatalogDto> getAgentCatalog() {
        try {
            return Optional.ofNullable(webClient.get().uri("/api/agent-catalog").retrieve()
                    .bodyToMono(AgentCatalogDto.class).block());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Status code to relay, plus the card when 200. Unreachable agent service → 502. */
    public record AgentCardResult(int status, java.util.Map<String, Object> card) {
    }

    @SuppressWarnings("unchecked")
    public AgentCardResult getProviderAgentCard(String provider, String name) {
        try {
            java.util.Map<String, Object> card = webClient.get()
                    .uri("/api/agent-catalog/providers/{provider}/agents/{name}/card", provider, name)
                    .retrieve().bodyToMono(java.util.Map.class).block();
            return card == null ? new AgentCardResult(502, null) : new AgentCardResult(200, card);
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            return new AgentCardResult(code == 404 ? 404 : 502, null);
        } catch (Exception e) {
            return new AgentCardResult(502, null);
        }
    }

    // ── A2A services ─────────────────────────────────────────────────────────

    public List<A2AServiceDto> listA2AServices() {
        try {
            var services = webClient.get().uri("/api/a2a-services").retrieve()
                    .bodyToFlux(A2AServiceDto.class).collectList().block();
            return services == null ? List.of() : services;
        } catch (Exception e) {
            throw upstreamUnavailable("A2A services could not be loaded from the agent service");
        }
    }

    public A2AServiceDto createA2AService(A2AServiceRequest request) {
        try {
            return webClient.post().uri("/api/a2a-services").bodyValue(request).retrieve()
                    .bodyToMono(A2AServiceDto.class).block();
        } catch (WebClientResponseException e) {
            throw a2aServiceError(e);
        }
    }

    public A2AServiceDto updateA2AService(String id, A2AServiceRequest request) {
        try {
            return webClient.put().uri("/api/a2a-services/{id}", id).bodyValue(request).retrieve()
                    .bodyToMono(A2AServiceDto.class).block();
        } catch (WebClientResponseException e) {
            throw a2aServiceError(e);
        }
    }

    public void deleteA2AService(String id) {
        try {
            webClient.delete().uri("/api/a2a-services/{id}", id).retrieve().toBodilessEntity().block();
        } catch (WebClientResponseException e) {
            throw a2aServiceError(e);
        }
    }

    // Validation, conflict and "no encryption key" answers reach the dashboard with embabel's message;
    // anything else is an upstream failure.
    private ResponseStatusException a2aServiceError(WebClientResponseException e) {
        int code = e.getStatusCode().value();
        if (code == 400 || code == 404 || code == 409 || code == 503) {
            return new ResponseStatusException(HttpStatus.valueOf(code), extractMessage(e, HttpStatus.valueOf(code).getReasonPhrase()));
        }
        return upstreamUnavailable("The agent service could not change the A2A service");
    }

    // ── Agent definitions ────────────────────────────────────────────────────

    public List<AgentSpecDto> listAgentSpecs() {
        try {
            var specs = webClient.get().uri("/api/agent-definitions").retrieve()
                    .bodyToFlux(AgentSpecDto.class).collectList().block();
            return specs != null ? specs : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<AgentSpecDto> getAgentSpec(String name) {
        try {
            var spec = webClient.get().uri("/api/agent-definitions/{name}", name).retrieve()
                    .bodyToMono(AgentSpecDto.class).block();
            return Optional.ofNullable(spec);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public AgentSpecDto createAgentSpec(AgentSpecDto request) {
        return webClient.post().uri("/api/agent-definitions").bodyValue(request).retrieve()
                .bodyToMono(AgentSpecDto.class).block();
    }

    public AgentSpecDto updateAgentSpec(String name, AgentSpecDto request) {
        return webClient.put().uri("/api/agent-definitions/{name}", name).bodyValue(request).retrieve()
                .bodyToMono(AgentSpecDto.class).block();
    }

    public void deleteAgentSpec(String name) {
        webClient.delete().uri("/api/agent-definitions/{name}", name).retrieve().toBodilessEntity().block();
    }

    public AgentSpecDto generateAgentSpec(String prompt) {
        return webClient.post().uri("/api/agent-definitions/generate-from-prompt")
                .bodyValue(new PromptRequestDto(prompt)).retrieve()
                .bodyToMono(AgentSpecDto.class).block();
    }

    // ── Subagent definitions ─────────────────────────────────────────────────

    public List<SubagentSpecDto> listSubagentSpecs() {
        try {
            var specs = webClient.get().uri("/api/subagent-definitions").retrieve()
                    .bodyToFlux(SubagentSpecDto.class).collectList().block();
            return specs != null ? specs : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<SubagentSpecDto> getSubagentSpec(String name) {
        try {
            var spec = webClient.get().uri("/api/subagent-definitions/{name}", name).retrieve()
                    .bodyToMono(SubagentSpecDto.class).block();
            return Optional.ofNullable(spec);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public SubagentSpecDto createSubagentSpec(SubagentSpecDto request) {
        return webClient.post().uri("/api/subagent-definitions").bodyValue(request).retrieve()
                .bodyToMono(SubagentSpecDto.class).block();
    }

    public SubagentSpecDto updateSubagentSpec(String name, SubagentSpecDto request) {
        return webClient.put().uri("/api/subagent-definitions/{name}", name).bodyValue(request).retrieve()
                .bodyToMono(SubagentSpecDto.class).block();
    }

    public void deleteSubagentSpec(String name) {
        webClient.delete().uri("/api/subagent-definitions/{name}", name).retrieve().toBodilessEntity().block();
    }

    public SubagentSpecDto generateSubagentSpec(String prompt) {
        return webClient.post().uri("/api/subagent-definitions/generate-from-prompt")
                .bodyValue(new PromptRequestDto(prompt)).retrieve()
                .bodyToMono(SubagentSpecDto.class).block();
    }

    // ── Skill definitions ────────────────────────────────────────────────────

    public List<SkillSpecDto> listSkillSpecs() {
        try {
            var specs = webClient.get().uri("/api/skill-definitions").retrieve()
                    .bodyToFlux(SkillSpecDto.class).collectList().block();
            return specs != null ? specs : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<SkillSpecDto> getSkillSpec(String name) {
        try {
            var spec = webClient.get().uri("/api/skill-definitions/{name}", name).retrieve()
                    .bodyToMono(SkillSpecDto.class).block();
            return Optional.ofNullable(spec);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public SkillSpecDto createSkillSpec(SkillSpecDto request) {
        return webClient.post().uri("/api/skill-definitions").bodyValue(request).retrieve()
                .bodyToMono(SkillSpecDto.class).block();
    }

    public SkillSpecDto updateSkillSpec(String name, SkillSpecDto request) {
        return webClient.put().uri("/api/skill-definitions/{name}", name).bodyValue(request).retrieve()
                .bodyToMono(SkillSpecDto.class).block();
    }

    public void deleteSkillSpec(String name) {
        webClient.delete().uri("/api/skill-definitions/{name}", name).retrieve().toBodilessEntity().block();
    }

    public SkillSpecDto generateSkillSpec(String prompt) {
        return webClient.post().uri("/api/skill-definitions/generate-from-prompt")
                .bodyValue(new PromptRequestDto(prompt)).retrieve()
                .bodyToMono(SkillSpecDto.class).block();
    }

    // ── Approval gate (workflow-approval-gate) ──────────────────────────────
    // ai-control-service performs zero gate business logic here: no stage-name check, no
    // feedbackSupported derivation. It relays, including the AC-59 400 and the 409 conflict.

    public Optional<ApprovalGateContextDto> getApprovalGateContext(String runId) {
        try {
            var context = webClient.get().uri("/api/agent-runs/{runId}/approval-gate", runId)
                    .retrieve().bodyToMono(ApprovalGateContextDto.class).block();
            return Optional.ofNullable(context);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        }
    }

    public ApprovalDecisionResultDto submitApprovalDecision(String runId, ApprovalDecisionRequestDto request) {
        try {
            return webClient.post().uri("/api/agent-runs/{runId}/approval-gate/decision", runId)
                    .bodyValue(request).retrieve()
                    .bodyToMono(ApprovalDecisionResultDto.class).block();
        } catch (WebClientResponseException.BadRequest e) {
            throw badRequest(e);
        } catch (WebClientResponseException.NotFound e) {
            throw notFound(e);
        } catch (WebClientResponseException.Conflict e) {
            throw conflict(e);
        }
    }

    // Mirrors badRequest(...)/conflict(...): AC-33's "unknown run" 404 must also reach the
    // dashboard with the original message, not the raw JSON body (D-QA-1).
    private ResponseStatusException notFound(WebClientResponseException.NotFound e) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, extractMessage(e, "Not Found"));
    }

    public List<ApprovalDecisionAuditEntryDto> listApprovalDecisions(String runId) {
        var entries = webClient.get().uri("/api/agent-runs/{runId}/approval-decisions", runId)
                .retrieve().bodyToFlux(ApprovalDecisionAuditEntryDto.class).collectList().block();
        return entries != null ? entries : List.of();
    }

    // Mirrors badRequest(...) above: embabel-agent-service's AC-31/AC-32 conflict body (already-
    // decided iteration, or a decision submitted while not currently awaiting approval) must reach
    // the dashboard as 409 with the original message, not as a 500.
    private ResponseStatusException conflict(WebClientResponseException.Conflict e) {
        return new ResponseStatusException(HttpStatus.CONFLICT, extractMessage(e, "Conflict"));
    }

    // Deliberately does NOT swallow failures into List.of() the way listSkillSpecs() above does.
    // The merged skill-catalog endpoint (BR-7) must be able to distinguish "embabel is down" from
    // "you have zero local skills" — the existing swallowing method stays exactly as it is for its
    // one remaining caller (GET /api/skill-definitions, AC-33).
    public List<SkillSpecDto> listSkillSpecsOrThrow() {
        var specs = webClient.get().uri("/api/skill-definitions").retrieve()
                .bodyToFlux(SkillSpecDto.class).collectList().block();
        return specs != null ? specs : List.of();
    }

    // ── Workflow execution history ───────────────────────────────────────────

    public WorkflowExecutionPageDto listWorkflowExecutionsOrThrow(String workflowId, Integer limit, Integer offset) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/workflows/{workflowId}/executions");
                        if (limit != null) uriBuilder.queryParam("limit", limit);
                        if (offset != null) uriBuilder.queryParam("offset", offset);
                        return uriBuilder.build(workflowId);
                    })
                    .retrieve()
                    .bodyToMono(WorkflowExecutionPageDto.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            throw notFound(e);
        }
    }

    public AgentRunDto getWorkflowExecutionOrThrow(String workflowId, String runId) {
        try {
            return webClient.get()
                    .uri("/api/workflows/{workflowId}/executions/{runId}", workflowId, runId)
                    .retrieve()
                    .bodyToMono(AgentRunDto.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            throw notFound(e);
        }
    }
}
