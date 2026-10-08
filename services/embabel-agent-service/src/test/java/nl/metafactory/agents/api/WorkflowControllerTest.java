package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.WorkflowExecutionService;
import nl.metafactory.agents.workflow.WorkflowDefinitionValidator;
import nl.metafactory.agents.workflow.WorkflowGroupRepository;
import nl.metafactory.agents.workflow.WorkflowLastExecutionService;
import nl.metafactory.agents.workflow.WorkflowOrbValidator;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import nl.metafactory.agents.workflow.model.WorkflowStartInput;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
@org.springframework.context.annotation.Import({WorkflowDefinitionValidator.class, WorkflowOrbValidator.class})
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private WorkflowDefinitionRepository repository;

    @MockitoBean
    private WorkflowGroupRepository groupRepository;

    @MockitoBean
    private WorkflowTriggerProperties workflowTriggerProperties;

    @MockitoBean
    private WorkflowExecutionService executionService;

    @MockitoBean
    private PolicyDecisionLogExportService decisionLogExportService;

    @MockitoBean
    private WorkflowLastExecutionService lastExecutionService;

    /**
     * workflow-execution-state-to-database: {@code list()} and {@code get(id)} pass every
     * definition through {@code lastExecutionService}. Unless a specific test overrides this
     * stub, this default identity pass-through preserves every pre-existing test's assertions,
     * which were written before this enrichment seam existed and assert on the definitions
     * exactly as returned by {@code repository}.
     */
    @BeforeEach
    void stubLastExecutionServiceAsIdentityByDefault() {
        when(lastExecutionService.withLastExecution(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lastExecutionService.withLastExecution(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WorkflowDefinition workflow(String id) {
        return new WorkflowDefinition(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
    }

    @Test
    void listReturnsAllWorkflows() throws Exception {
        when(repository.findAll()).thenReturn(List.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("wf-1"));
    }

    @Test
    void listEnrichesWorkflowsWithLastExecutionSummaryFromTheLastExecutionService() throws Exception {
        when(repository.findAll()).thenReturn(List.of(workflow("wf-1")));
        WorkflowDefinition enriched = workflow("wf-1").withLastExecution("COMPLETED", Instant.parse("2024-05-01T10:00:00Z"));
        when(lastExecutionService.withLastExecution(anyList())).thenReturn(List.of(enriched));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastExecutionStatus").value("COMPLETED"));
    }

    @Test
    void getReturnsWorkflowById() throws Exception {
        when(repository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows/wf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Onboarding"));
    }

    @Test
    void getEnrichesTheWorkflowWithLastExecutionSummaryFromTheLastExecutionService() throws Exception {
        when(repository.findById("wf-1")).thenReturn(Optional.of(workflow("wf-1")));
        WorkflowDefinition enriched = workflow("wf-1").withLastExecution("FAILED", Instant.parse("2024-05-02T11:00:00Z"));
        when(lastExecutionService.withLastExecution(any(WorkflowDefinition.class))).thenReturn(enriched);

        mockMvc.perform(get("/api/workflows/wf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastExecutionStatus").value("FAILED"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workflows/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        var wf = workflow("wf-1");
        when(repository.save(any())).thenReturn(wf);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wf-1"));
    }

    @Test
    void updateReturnsUpdatedWorkflow() throws Exception {
        var wf = workflow("wf-1");
        when(repository.findById("wf-1")).thenReturn(Optional.of(wf));
        when(repository.save(any())).thenReturn(wf);

        mockMvc.perform(put("/api/workflows/wf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wf-1"));
    }

    @Test
    void createReturns400WithoutGroupAndProject() throws Exception {
        var invalid = new WorkflowDefinition("wf-x", "Loose workflow", "", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createAcceptsWorkflowWithoutProjectWhenGrouped() throws Exception {
        var grouped = new WorkflowDefinition("wf-g", "Grouped workflow", "", "wg-spec-workflow", "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        when(repository.save(any())).thenReturn(grouped);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grouped)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value("wg-spec-workflow"));
    }

    @Test
    void updateReturns400WithoutGroupAndProject() throws Exception {
        var invalid = new WorkflowDefinition("wf-x", "Loose workflow", "  ", "", "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);

        mockMvc.perform(put("/api/workflows/wf-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        var wf = workflow("missing");
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/workflows/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wf)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mockMvc.perform(delete("/api/workflows/wf-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns409WhenWorkflowIsReferencedByAnotherWorkflowsOrb() throws Exception {
        var referencingWorkflow = new WorkflowDefinition("wf-parent", "Parent", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null,
                List.of(new nl.metafactory.agents.workflow.model.WorkflowOrb("wf-1",
                        nl.metafactory.agents.workflow.model.WorkflowOrbMode.SEQUENTIAL, null)));
        when(repository.findAll()).thenReturn(List.of(referencingWorkflow));

        mockMvc.perform(delete("/api/workflows/wf-1"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void startReturnsExecutionResponse() throws Exception {
        when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), any())).thenReturn(
                new WorkflowStartResponse("wf-1", "run-1", "RUNNING", Instant.now(), "Workflow started with agents: [requirement]"));

        mockMvc.perform(post("/api/workflows/wf-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-1"))
                .andExpect(jsonPath("$.executionId").value("run-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        org.mockito.Mockito.verify(executionService).startWorkflow(
                org.mockito.ArgumentMatchers.eq("wf-1"), org.mockito.ArgumentMatchers.eq(WorkflowStartInput.empty()), any());
    }

    @Test
    void startBuildsInitiatorFromTheAuthenticatedJwtNotTheRequestBody() throws Exception {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "alice")
                .claim("sub", "sub-alice")
                .build();
        var authentication = new org.springframework.security.authentication.TestingAuthenticationToken(jwt, null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), any())).thenReturn(
                    new WorkflowStartResponse("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

            mockMvc.perform(post("/api/workflows/wf-1/start"))
                    .andExpect(status().isOk());

            var captor = org.mockito.ArgumentCaptor.forClass(nl.metafactory.agents.model.RunInitiator.class);
            org.mockito.Mockito.verify(executionService).startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue())
                    .isEqualTo(nl.metafactory.agents.model.RunInitiator.human("alice", "sub-alice"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void startPassesPromptAndSpecFileToExecutionService() throws Exception {
        var input = new WorkflowStartInput("Create a spec for login", "001-example-feature/spec.md",
                "https://github.com/org/repo.git", "bot", "secret", null);
        when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), any())).thenReturn(
                new WorkflowStartResponse("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(executionService).startWorkflow(
                org.mockito.ArgumentMatchers.eq("wf-1"), org.mockito.ArgumentMatchers.eq(input), any());
    }

    // ── MADP-54 AC-08 (receive half): the exact body ai-control records binds baseBranch by name ──

    @Test
    void startBindsBaseBranchFromTheRequestBodyByComponentName() throws Exception {
        when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), any())).thenReturn(
                new WorkflowStartResponse("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        // this is the shape EmbabelAgentClientTest records: property name == record component name
        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Create a spec\",\"projectId\":\"ignored-by-embabel\",\"baseBranch\":\"develop\"}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowStartInput.class);
        org.mockito.Mockito.verify(executionService).startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().baseBranch()).isEqualTo("develop");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().prompt()).isEqualTo("Create a spec");
    }

    @Test
    void startBindsBaseBranchToNullWhenTheRequestBodyOmitsIt() throws Exception {
        when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), any(), any())).thenReturn(
                new WorkflowStartResponse("wf-1", "run-1", "RUNNING", Instant.now(), "started"));

        mockMvc.perform(post("/api/workflows/wf-1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Create a spec\"}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowStartInput.class);
        org.mockito.Mockito.verify(executionService).startWorkflow(org.mockito.ArgumentMatchers.eq("wf-1"), captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().baseBranch()).isNull();
    }

    @Test
    void startReturns404WhenWorkflowMissing() throws Exception {
        when(executionService.startWorkflow(org.mockito.ArgumentMatchers.eq("missing"), any(), any()))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Workflow not found: missing"));

        mockMvc.perform(post("/api/workflows/missing/start"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportReturnsGroupsAndWorkflowsAsBundle() throws Exception {
        when(groupRepository.findAll()).thenReturn(List.of(new WorkflowGroup("wg-1", "Onboarding flows", "desc", "Noordzee Logistics")));
        when(repository.findAll()).thenReturn(List.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].id").value("wg-1"))
                .andExpect(jsonPath("$.workflows[0].id").value("wf-1"))
                .andExpect(jsonPath("$.workflows[0].agentIds[0]").value("requirement"));
    }

    @Test
    void exportDoesNotEnrichTheExportedWorkflowsWithLastExecutionSummary() throws Exception {
        // workflow-execution-state-to-database, ADR-D2/S2: export() is a deliberate, tested
        // invariant that must never enrich the bundle — a later importBundle -> repository.save
        // would otherwise round-trip runtime state back onto disk (BR-2/BR-5). This asserts both
        // that the exported bundle carries the null pair AND that lastExecutionService is never
        // invoked on the export path.
        when(groupRepository.findAll()).thenReturn(List.of());
        when(repository.findAll()).thenReturn(List.of(workflow("wf-1")));

        mockMvc.perform(get("/api/workflows/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflows[0].lastExecutionStatus").doesNotExist())
                .andExpect(jsonPath("$.workflows[0].lastExecutionAt").doesNotExist());

        org.mockito.Mockito.verifyNoInteractions(lastExecutionService);
    }

    @Test
    void importSavesGroupsAndWorkflowsAndReturnsCounts() throws Exception {
        var bundle = new WorkflowExportBundle(
                List.of(new WorkflowGroup("wg-1", "Onboarding flows", "desc", "Noordzee Logistics")),
                List.of(workflow("wf-1"), workflow("wf-2")));
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupsImported").value(1))
                .andExpect(jsonPath("$.workflowsImported").value(2))
                .andExpect(jsonPath("$.violations").isArray());

        org.mockito.Mockito.verify(groupRepository).save(any());
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(WorkflowDefinition.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());
        savedCaptor.getAllValues().forEach(saved -> org.assertj.core.api.Assertions.assertThat(saved.agentIds()).containsExactly("requirement"));
    }

    @Test
    void importReportsUnresolvableOrbReferenceWithoutRejectingTheImport() throws Exception {
        var withDanglingOrb = new WorkflowDefinition("wf-orb-1", "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null,
                List.of(new nl.metafactory.agents.workflow.model.WorkflowOrb("wf-does-not-exist-anywhere",
                        nl.metafactory.agents.workflow.model.WorkflowOrbMode.SEQUENTIAL, null)));
        var bundle = new WorkflowExportBundle(List.of(), List.of(withDanglingOrb));
        when(workflowTriggerProperties.getMaxOrbsPerWorkflow()).thenReturn(5);
        when(workflowTriggerProperties.getMaxChainDepth()).thenReturn(3);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById("wf-does-not-exist-anywhere")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowsImported").value(1))
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("wf-does-not-exist-anywhere")));

        org.mockito.Mockito.verify(repository).save(any());
    }

    @Test
    void importReturns400WhenBundleContainsWorkflowWithoutGroupAndProject() throws Exception {
        var invalid = new WorkflowDefinition("wf-x", "Loose workflow", "", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        var bundle = new WorkflowExportBundle(List.of(), List.of(invalid));

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void importTreatsMissingListsAsEmpty() throws Exception {
        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupsImported").value(0))
                .andExpect(jsonPath("$.workflowsImported").value(0));
    }

    @Test
    void decisionLogsReturnsExportedEntries() throws Exception {
        var entry = new PolicyDecisionAuditEntry("d-1", "wf-1", "run-1", null, "Noordzee Logistics", null, null,
                "requirement", null, null, null, "dashboard-button", "agent.use", "opa-decision-1", "ALLOWED",
                "allowed by policy", "low", false, List.of("agent.use"), Instant.now(), "v1", false, null);
        when(decisionLogExportService.exportDecisionLogsForWorkflow(any(), any())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/workflows/wf-1/decision-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].opaDecisionResult").value("ALLOWED"));
    }

    @Test
    void createReturns400WhenAgentIdsIsEmpty() throws Exception {
        var invalid = new WorkflowDefinition("wf-x", "Loose workflow", "Noordzee Logistics", null, "desc",
                List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateReturns400WhenAgentIdsIsEmpty() throws Exception {
        var invalid = new WorkflowDefinition("wf-1", "Loose workflow", "Noordzee Logistics", null, "desc",
                List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        mockMvc.perform(put("/api/workflows/wf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void importReturns400WhenBundleContainsWorkflowWithEmptyAgentIds() throws Exception {
        var invalid = new WorkflowDefinition("wf-x", "Loose workflow", "Noordzee Logistics", null, "desc",
                List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null);
        var bundle = new WorkflowExportBundle(List.of(), List.of(invalid));
        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
