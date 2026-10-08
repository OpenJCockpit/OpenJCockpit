package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.policy.PolicyDecisionLogExportService;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.WorkflowExecutionService;
import nl.metafactory.agents.workflow.WorkflowDefinitionValidator;
import nl.metafactory.agents.workflow.WorkflowGroupRepository;
import nl.metafactory.agents.workflow.WorkflowLastExecutionService;
import nl.metafactory.agents.workflow.WorkflowOrbValidator;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-03 (server 400 naming the offending stage) and AC-03b (a non-realisation placement is valid
 * configuration, not a validation error) for {@code WorkflowController.validateApprovalGate}.
 */
@WebMvcTest(
        controllers = WorkflowController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
@org.springframework.context.annotation.Import({WorkflowDefinitionValidator.class, WorkflowOrbValidator.class})
class WorkflowControllerApprovalGateValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper objectMapper;
    @MockitoBean WorkflowDefinitionRepository repository;
    @MockitoBean WorkflowGroupRepository groupRepository;
    @MockitoBean WorkflowTriggerProperties workflowTriggerProperties;
    @MockitoBean WorkflowExecutionService executionService;
    @MockitoBean PolicyDecisionLogExportService decisionLogExportService;
    @MockitoBean WorkflowLastExecutionService lastExecutionService;

    private WorkflowDefinition workflow(String id, List<String> agentIds, ApprovalGateConfig gate) {
        return new WorkflowDefinition(id, "Onboarding", "Noordzee Logistics", null, "desc",
                agentIds, List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, gate, null);
    }

    // Note on message-content verification: MockMvc in a @WebMvcTest slice does not perform
    // container-level error-page dispatch, so an uncaught ResponseStatusException's reason is not
    // observable in MockHttpServletResponse's body here (this is a MockMvc/slice-testing
    // limitation, not specific to this endpoint — the pre-existing requireGroupOrProject 400 has
    // the same property and was never asserted on body content either). `server.error.include
    // -message: always` (application.yml) is what makes the real, deployed server expose the
    // message in the body; that the message then survives the ai-control-service relay in both
    // directions is what EmbabelAgentClientApprovalGateTest / EmbabelAgentClientTest (MockWebServer,
    // a real HTTP body) and WorkflowControllerTest's F1 tests actually verify against real bytes.
    // This test asserts the two things a slice test *can* observe: the 400 status and the "stored
    // workflow is unchanged" side-effect-free guarantee (AC-03).

    @Test
    void createRejectsAGatePlacedAfterAStageNotInAgentIds() throws Exception {
        var invalid = workflow("wf-1", List.of("requirement"), new ApprovalGateConfig(true, "realisation"));

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAGatePlacedAfterAStageNotInAgentIds() throws Exception {
        var invalid = workflow("wf-1", List.of("requirement"), new ApprovalGateConfig(true, "review"));
        when(repository.findById("wf-1")).thenReturn(java.util.Optional.of(workflow("wf-1",
                List.of("requirement"), null)));

        mockMvc.perform(put("/api/workflows/wf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    void importRejectsABundleWhoseWorkflowGateReferencesAnUnselectedStage() throws Exception {
        var invalid = workflow("wf-1", List.of("requirement"), new ApprovalGateConfig(true, "evidence"));
        var bundle = new WorkflowExportBundle(List.of(), List.of(invalid));

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    void createAcceptsADisabledGateEvenIfPlacementStageIsStale() throws Exception {
        var disabledGate = workflow("wf-2", List.of("requirement"), new ApprovalGateConfig(false, "realisation"));
        when(repository.save(any())).thenReturn(disabledGate);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disabledGate)))
                .andExpect(status().isCreated());
    }

    @Test
    void createAcceptsANonRealisationPlacementAsValidConfiguration() throws Exception {
        // AC-03b: BR-04's validation is about the stage being selected, independent of whether it
        // supports loop-back. A gate after `impact` is valid configuration, not an error.
        var validNonRealisation = workflow("wf-3", List.of("impact"), new ApprovalGateConfig(true, "impact"));
        when(repository.save(any())).thenReturn(validNonRealisation);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validNonRealisation)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalGate.placementStage").value("impact"));

        verify(repository).save(any());
    }

    @Test
    void createAcceptsAWorkflowWithNoGateAtAll() throws Exception {
        var noGate = workflow("wf-4", List.of("requirement"), null);
        when(repository.save(any())).thenReturn(noGate);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noGate)))
                .andExpect(status().isCreated());
    }
}
