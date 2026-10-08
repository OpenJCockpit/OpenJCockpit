package nl.metafactory.aicontrol.client;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.config.BearerTokenRelayFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbabelAgentClientTest {

    private MockWebServer server;
    private EmbabelAgentClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .filter(new BearerTokenRelayFilter())
                .build();
        client = new EmbabelAgentClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLatestRunReturnsRun() throws Exception {
        var run = new AgentRunDto("run-1", "cust", "spec.md", "https://github.com/org/repo",
                                  "COMPLETED", Instant.now(), List.of(), List.of("artifact.md"), null, null, null, null);
        server.enqueue(new MockResponse()
            .setBody(mapper.writeValueAsString(run))
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getLatestRun("run-1");

        assertThat(result).isPresent();
        assertThat(result.get().runId()).isEqualTo("run-1");
        assertThat(result.get().repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(result.get().status()).isEqualTo("COMPLETED");
    }

    @Test
    void getLatestRunReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        var result = client.getLatestRun("unknown-run");

        assertThat(result).isEmpty();
    }

    @Test
    void getLatestRunReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        var result = client.getLatestRun("any-run");

        assertThat(result).isEmpty();
    }

    @Test
    void getAgentDefinitionsReturnsOrderedList() throws Exception {
        var definitions = List.of(
                new AgentDefinitionDto("requirement", "Requirement Agent", "desc", "specification",
                        0, "SpecContent", "RequirementAnalysis"),
                new AgentDefinitionDto("impact", "Impact Analysis Agent", "desc", "analysis",
                        1, "RequirementAnalysis", "ImpactReport")
        );
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(definitions))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getAgentDefinitions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("requirement");
        assertThat(result.get(0).sequenceOrder()).isEqualTo(0);
        assertThat(result.get(1).inputType()).isEqualTo("RequirementAnalysis");
    }

    @Test
    void getAgentDefinitionsReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        var result = client.getAgentDefinitions();

        assertThat(result).isEmpty();
    }

    @Test
    void startAgentRunPostsRequestAndReturnsRun() throws Exception {
        var run = new AgentRunDto("run-99", "cust", "spec content",
                "https://github.com/org/repo", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(run))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var request = new AgentRunRequestDto("cust", "spec content",
                List.of("requirement"), "user", "https://github.com/org/repo");
        var result = client.startAgentRun(request);

        assertThat(result.runId()).isEqualTo("run-99");
        assertThat(result.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(result.status()).isEqualTo("RUNNING");

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/agent-runs");
    }

    @Test
    void stopAgentRunSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.stopAgentRun("run-55");

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/agent-runs/run-55");
    }

    @Test
    void startAgentRunForwardsBearerToken() throws Exception {
        var jwt = Jwt.withTokenValue("test-bearer-token")
                .header("alg", "RS256")
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        var run = new AgentRunDto("run-1", "cust", "spec",
                "https://github.com/org/repo", "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(run))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.startAgentRun(new AgentRunRequestDto("cust", "spec",
                List.of("requirement"), "user", "https://github.com/org/repo"));

        var recorded = server.takeRequest();
        assertThat(recorded.getHeader(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer test-bearer-token");
    }

    // ── Workflows ────────────────────────────────────────────────────────────

    private WorkflowDefinitionDto workflow(String id) {
        return new WorkflowDefinitionDto(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null,
                null, null);
    }

    @Test
    void listWorkflowsReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(workflow("wf-1"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listWorkflows()).extracting(WorkflowDefinitionDto::id).containsExactly("wf-1");
    }

    @Test
    void listWorkflowsThrowsBadGatewayOnConnectionError() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.listWorkflows())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(rse.getReason())
                            .isEqualTo("Workflows could not be loaded from the agent service");
                    // Must never relay the transport exception's text, which embeds host:port.
                    assertThat(rse.getReason()).doesNotContain("localhost").doesNotContain(":");
                });
    }

    @Test
    void listWorkflowsThrowsBadGatewayCarryingTheUpstreamMessageOnServerError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"message\":\"Failed to read definition file 'workflows/wf-x.yaml': "
                        + "StreamReadException at line 7, column 3\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.listWorkflows())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .contains("workflows/wf-x.yaml"));
    }

    @Test
    void getWorkflowReturnsWorkflow() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(workflow("wf-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.getWorkflow("wf-1")).isPresent();
    }

    @Test
    void getWorkflowReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getWorkflow("missing")).isEmpty();
    }

    @Test
    void getWorkflowReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.getWorkflow("wf-1")).isEmpty();
    }

    @Test
    void createWorkflowPostsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(workflow("wf-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.createWorkflow(workflow("wf-1"));

        assertThat(result.id()).isEqualTo("wf-1");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflows");
    }

    // D-QA-1 (workflow-approval-gate QA report §9): embabel-agent-service's real, deployed error
    // body is the standard Spring Boot shape below (confirmed live against a real running
    // container) — NOT a bare plain-text string. A hand-crafted plain-text mock body here would
    // exercise a shape the real server never actually produces and would pass "vacuously" (QA's
    // own finding). EmbabelAgentClient.extractMessage(...) parses this shape and relays only the
    // clean "message" value, never the raw JSON blob.
    private String springBootErrorBody(int status, String error, String message, String path) {
        return "{\"timestamp\":\"2026-01-01T00:00:00.000+00:00\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"path\":\"" + path + "\"}";
    }

    @Test
    void createWorkflowPropagatesValidationErrorAs400() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody(springBootErrorBody(400, "Bad Request",
                        "A workflow without a group must be linked to a project", "/api/workflows"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.createWorkflow(workflow("wf-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("A workflow without a group must be linked to a project")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void updateWorkflowPropagatesValidationErrorAs400() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody(springBootErrorBody(400, "Bad Request",
                        "A workflow without a group must be linked to a project", "/api/workflows/wf-1"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.updateWorkflow("wf-1", workflow("wf-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("A workflow without a group must be linked to a project")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void importWorkflowsPropagatesValidationErrorAs400() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody(springBootErrorBody(400, "Bad Request",
                        "A workflow without a group must be linked to a project", "/api/workflows/import"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.importWorkflows(new WorkflowExportBundleDto(List.of(), List.of(workflow("wf-1")))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("A workflow without a group must be linked to a project")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    @Test
    void createWorkflowFallsBackToGenericReasonWhenErrorBodyIsNotJson() {
        // Defensive path: if the upstream body is ever not JSON (or has no message field), the
        // relay must still produce a real ResponseStatusException with a sane, non-blank reason —
        // never leak the raw body, never throw its own unrelated exception.
        server.enqueue(new MockResponse().setResponseCode(400).setBody("not json at all"));

        assertThatThrownBy(() -> client.createWorkflow(workflow("wf-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Bad Request");
    }

    @Test
    void createWorkflowFallsBackToGenericReasonWhenUpstreamJsonHasNoUsableMessage() {
        // Distinct defensive path from the "not JSON at all" case above: the body IS valid JSON
        // and decodes without throwing, but its "message" is blank — extractMessage(...) must
        // still fall back to a generic reason rather than surfacing an empty string.
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"message\":\"\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.createWorkflow(workflow("wf-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Bad Request");
    }

    @Test
    void updateWorkflowPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(workflow("wf-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.updateWorkflow("wf-1", workflow("wf-1"));

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/wf-1");
    }

    @Test
    void deleteWorkflowSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteWorkflow("wf-1");

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/wf-1");
    }

    @Test
    void deleteWorkflowPropagates409AsResponseStatusException() {
        server.enqueue(new MockResponse().setResponseCode(409)
                .setBody(springBootErrorBody(409, "Conflict",
                        "Cannot delete workflow 'wf-1': it is still referenced by workflow orb(s) in: [wf-2]",
                        "/api/workflows/wf-1"))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThatThrownBy(() -> client.deleteWorkflow("wf-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("still referenced by workflow orb(s) in: [wf-2]")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\"timestamp\""));
    }

    // ── Workflow groups & export/import ─────────────────────────────────────

    private WorkflowGroupDto group(String id) {
        return new WorkflowGroupDto(id, "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
    }

    @Test
    void listWorkflowGroupsReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(group("wg-1"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listWorkflowGroups()).extracting(WorkflowGroupDto::id).containsExactly("wg-1");
    }

    @Test
    void listWorkflowGroupsReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.listWorkflowGroups()).isEmpty();
    }

    @Test
    void getWorkflowGroupReturnsGroup() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(group("wg-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.getWorkflowGroup("wg-1")).isPresent();
    }

    @Test
    void getWorkflowGroupReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getWorkflowGroup("missing")).isEmpty();
    }

    @Test
    void getWorkflowGroupReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.getWorkflowGroup("wg-1")).isEmpty();
    }

    @Test
    void createWorkflowGroupPostsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(group("wg-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.createWorkflowGroup(group("wg-1"));

        assertThat(result.id()).isEqualTo("wg-1");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflow-groups");
    }

    @Test
    void updateWorkflowGroupPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(group("wg-1")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.updateWorkflowGroup("wg-1", group("wg-1"));

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/workflow-groups/wg-1");
    }

    @Test
    void deleteWorkflowGroupSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteWorkflowGroup("wg-1");

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/workflow-groups/wg-1");
    }

    @Test
    void exportWorkflowsReturnsBundle() throws Exception {
        var bundle = new WorkflowExportBundleDto(List.of(group("wg-1")), List.of(workflow("wf-1")));
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(bundle))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.exportWorkflows();

        assertThat(result.groups()).extracting(WorkflowGroupDto::id).containsExactly("wg-1");
        assertThat(result.workflows()).extracting(WorkflowDefinitionDto::id).containsExactly("wf-1");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflows/export");
    }

    @Test
    void importWorkflowsPostsBundleAndReturnsCounts() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(new WorkflowImportResultDto(1, 2,
                        List.of("workflow orb in 'wf-1' references unknown workflow 'wf-missing'"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.importWorkflows(new WorkflowExportBundleDto(List.of(group("wg-1")), List.of(workflow("wf-1"))));

        assertThat(result.groupsImported()).isEqualTo(1);
        assertThat(result.workflowsImported()).isEqualTo(2);
        assertThat(result.violations()).containsExactly("workflow orb in 'wf-1' references unknown workflow 'wf-missing'");
        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/import");
    }

    @Test
    void startWorkflowSendsPromptAndSpecFileInBody() throws Exception {
        var response = new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.startWorkflow("wf-1", new WorkflowStartInputDto("Create a spec", "001-example-feature/spec.md",
                "https://github.com/org/repo.git", null, "bot", "secret", "develop"));

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/workflows/wf-1/start");
        assertThat(recorded.getBody().readUtf8())
                .contains("Create a spec")
                .contains("001-example-feature/spec.md")
                // AC-08 (send half): the JSON property name is the record component name verbatim,
                // proving no @JsonProperty/@JsonNaming is needed anywhere on this path.
                .contains("\"baseBranch\":\"develop\"");
    }

    @Test
    void startWorkflowWithoutInputSendsEmptyBody() throws Exception {
        var response = new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.startWorkflow("wf-1", null);

        var recorded = server.takeRequest();
        assertThat(recorded.getBody().readUtf8()).doesNotContain("Create a spec");
    }

    @Test
    void startWorkflowReturnsResponse() throws Exception {
        var response = new WorkflowStartResponseDto("wf-1", "run-1", "RUNNING", Instant.now(), "started");
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(response))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.startWorkflow("wf-1");

        assertThat(result.executionId()).isEqualTo("run-1");
    }

    @Test
    void startWorkflowThrowsNotFoundWhenWorkflowMissing() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.startWorkflow("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("missing");
    }

    // ── Decision logs and OPA configuration ──────────────────────────────────

    private PolicyDecisionAuditEntryDto decisionEntry(String id) {
        return new PolicyDecisionAuditEntryDto(id, "wf-1", "run-1", null, "Noordzee Logistics", null, null,
                "requirement", null, null, null, "dashboard-button", "agent.use", "opa-decision-1", "ALLOWED",
                "allowed by policy", "low", false, List.of("agent.use"), Instant.now(), "v1", false, null);
    }

    @Test
    void getDecisionLogsForWorkflowReturnsEntriesAndAppliesFilters() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(decisionEntry("d-1"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getDecisionLogsForWorkflow("wf-1", Instant.now(), Instant.now(),
                "requirement", "log-collector", "summarize", "jira.createIssue", "ALLOWED");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("d-1");
        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).startsWith("/api/workflows/wf-1/decision-logs?");
        assertThat(recorded.getPath()).contains("agentId=requirement").contains("result=ALLOWED");
    }

    @Test
    void getDecisionLogsForWorkflowReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        var result = client.getDecisionLogsForWorkflow("wf-1", null, null, null, null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getDecisionLogsForWorkflowExecutionReturnsEntries() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(decisionEntry("d-2"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getDecisionLogsForWorkflowExecution("run-1", null, null, null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/workflow-executions/run-1/decision-logs");
    }

    @Test
    void getDecisionLogsForWorkflowExecutionReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        var result = client.getDecisionLogsForWorkflowExecution("run-1", null, null, null, null, null, null, null);

        assertThat(result).isEmpty();
    }

    private OpaConfigDto opaConfig() {
        return new OpaConfigDto(true, "http://localhost:8181", "/v1/data/openjcockpit/workflow/decision",
                "/health", 3, "FAIL_CLOSED", true, true, "staging", "noordzee", "onboarding", false);
    }

    @Test
    void getOpaConfigReturnsConfig() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(opaConfig()))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.getOpaConfig();

        assertThat(result.baseUrl()).isEqualTo("http://localhost:8181");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/opa-config");
    }

    @Test
    void saveOpaConfigPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(opaConfig()))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        var request = new OpaConfigRequestDto(true, "http://localhost:8181", null, null, null, null,
                null, null, null, null, null, null);

        var result = client.saveOpaConfig(request);

        assertThat(result.enabled()).isTrue();
        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/opa-config");
    }

    @Test
    void checkOpaHealthReturnsTrueWhenReachable() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"reachable\": true}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.checkOpaHealth()).isTrue();
    }

    @Test
    void checkOpaHealthReturnsFalseOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.checkOpaHealth()).isFalse();
    }

    // ── Agent definitions ────────────────────────────────────────────────────

    private AgentSpecDto agentSpec(String name) {
        return new AgentSpecDto(name, "desc", "role", "instructions", List.of(), List.of(), List.of(), "wf-1", false);
    }

    @Test
    void listAgentSpecsReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(agentSpec("triage-agent"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listAgentSpecs()).extracting(AgentSpecDto::name).containsExactly("triage-agent");
    }

    @Test
    void listAgentSpecsReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.listAgentSpecs()).isEmpty();
    }

    @Test
    void getAgentSpecReturnsSpec() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(agentSpec("triage-agent")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.getAgentSpec("triage-agent")).isPresent();
    }

    @Test
    void getAgentSpecReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getAgentSpec("missing")).isEmpty();
    }

    @Test
    void getAgentSpecReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.getAgentSpec("triage-agent")).isEmpty();
    }

    @Test
    void createAgentSpecPostsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(agentSpec("triage-agent")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.createAgentSpec(agentSpec("triage-agent")).name()).isEqualTo("triage-agent");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-definitions");
    }

    @Test
    void updateAgentSpecPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(agentSpec("triage-agent")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.updateAgentSpec("triage-agent", agentSpec("triage-agent"));

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/agent-definitions/triage-agent");
    }

    @Test
    void deleteAgentSpecSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteAgentSpec("triage-agent");

        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }

    @Test
    void generateAgentSpecPostsPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(agentSpec("triage-agent")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.generateAgentSpec("Triage incoming issues");

        assertThat(result.name()).isEqualTo("triage-agent");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/agent-definitions/generate-from-prompt");
    }

    // ── Subagent definitions ─────────────────────────────────────────────────

    private SubagentSpecDto subagentSpec(String name) {
        return new SubagentSpecDto(name, "parent", "desc", "responsibilities", "instructions", List.of(), List.of(), "wf-1");
    }

    @Test
    void listSubagentSpecsReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(subagentSpec("log-collector"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listSubagentSpecs()).extracting(SubagentSpecDto::name).containsExactly("log-collector");
    }

    @Test
    void listSubagentSpecsReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.listSubagentSpecs()).isEmpty();
    }

    @Test
    void getSubagentSpecReturnsSpec() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(subagentSpec("log-collector")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.getSubagentSpec("log-collector")).isPresent();
    }

    @Test
    void getSubagentSpecReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getSubagentSpec("missing")).isEmpty();
    }

    @Test
    void getSubagentSpecReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.getSubagentSpec("log-collector")).isEmpty();
    }

    @Test
    void createSubagentSpecPostsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(subagentSpec("log-collector")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.createSubagentSpec(subagentSpec("log-collector")).name()).isEqualTo("log-collector");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/subagent-definitions");
    }

    @Test
    void updateSubagentSpecPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(subagentSpec("log-collector")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.updateSubagentSpec("log-collector", subagentSpec("log-collector"));

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/subagent-definitions/log-collector");
    }

    @Test
    void deleteSubagentSpecSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteSubagentSpec("log-collector");

        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }

    @Test
    void generateSubagentSpecPostsPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(subagentSpec("log-collector")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.generateSubagentSpec("Collect logs");

        assertThat(result.name()).isEqualTo("log-collector");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/subagent-definitions/generate-from-prompt");
    }

    // ── Skill definitions ────────────────────────────────────────────────────

    private SkillSpecDto skillSpec(String name) {
        return new SkillSpecDto(name, "desc", "in", "out", "instructions", List.of(), null);
    }

    @Test
    void listSkillSpecsReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(skillSpec("summarize"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listSkillSpecs()).extracting(SkillSpecDto::name).containsExactly("summarize");
    }

    @Test
    void listSkillSpecsReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.listSkillSpecs()).isEmpty();
    }

    @Test
    void getSkillSpecReturnsSpec() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(skillSpec("summarize")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.getSkillSpec("summarize")).isPresent();
    }

    @Test
    void getSkillSpecReturnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.getSkillSpec("missing")).isEmpty();
    }

    @Test
    void getSkillSpecReturnsEmptyOnConnectionError() throws IOException {
        server.shutdown();

        assertThat(client.getSkillSpec("summarize")).isEmpty();
    }

    @Test
    void createSkillSpecPostsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(skillSpec("summarize")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.createSkillSpec(skillSpec("summarize")).name()).isEqualTo("summarize");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/skill-definitions");
    }

    @Test
    void updateSkillSpecPutsRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(skillSpec("summarize")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.updateSkillSpec("summarize", skillSpec("summarize"));

        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("PUT");
        assertThat(recorded.getPath()).isEqualTo("/api/skill-definitions/summarize");
    }

    @Test
    void deleteSkillSpecSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteSkillSpec("summarize");

        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }

    @Test
    void generateSkillSpecPostsPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(skillSpec("summarize")))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        var result = client.generateSkillSpec("Summarize text");

        assertThat(result.name()).isEqualTo("summarize");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/skill-definitions/generate-from-prompt");
    }

    // ── listSkillSpecsOrThrow (skills-tab-marketplace-import, B4) ───────────────
    // BR-7: "embabel is down" must be distinguishable from "zero local skills" — unlike
    // listSkillSpecs() above, this method must NOT swallow a failure into an empty list.

    @Test
    void listSkillSpecsOrThrowReturnsList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(List.of(skillSpec("summarize"))))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.listSkillSpecsOrThrow()).extracting(SkillSpecDto::name).containsExactly("summarize");
    }

    @Test
    void listSkillSpecsOrThrowPropagatesConnectionErrorInsteadOfSwallowingIt() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.listSkillSpecsOrThrow()).isNotNull();
    }

    // AC-33 regression guard: the pre-existing listSkillSpecs() must keep swallowing failures,
    // since GET /api/skill-definitions' current behaviour must remain byte-identical.
    @Test
    void listSkillSpecsStillSwallowsConnectionErrorsIntoAnEmptyList() throws IOException {
        server.shutdown();

        assertThat(client.listSkillSpecs()).isEmpty();
    }
}
