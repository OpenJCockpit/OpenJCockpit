package nl.metafactory.agents.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.workflow.WorkflowDefinitionRepository;
import nl.metafactory.agents.workflow.WorkflowGroupRepository;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowGroupController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class WorkflowGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private WorkflowGroupRepository repository;

    @MockitoBean
    private WorkflowDefinitionRepository workflowRepository;

    private WorkflowGroup group(String id) {
        return new WorkflowGroup(id, "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
    }

    private WorkflowDefinition workflow(String id, String groupId) {
        return workflow(id, groupId, null);
    }

    private WorkflowDefinition workflow(String id, String groupId,
                                         nl.metafactory.agents.approval.model.ApprovalGateConfig gate) {
        return new WorkflowDefinition(id, "Onboarding", "Noordzee Logistics", groupId, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null,
                null, gate, null);
    }

    @Test
    void listReturnsAllGroups() throws Exception {
        when(repository.findAll()).thenReturn(List.of(group("wg-1")));

        mockMvc.perform(get("/api/workflow-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("wg-1"))
                .andExpect(jsonPath("$[0].name").value("Onboarding flows"));
    }

    @Test
    void getReturnsGroupById() throws Exception {
        when(repository.findById("wg-1")).thenReturn(Optional.of(group("wg-1")));

        mockMvc.perform(get("/api/workflow-groups/wg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Onboarding flows"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workflow-groups/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        var g = group("wg-1");
        when(repository.save(any())).thenReturn(g);

        mockMvc.perform(post("/api/workflow-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(g)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wg-1"));
    }

    @Test
    void updateReturnsUpdatedGroup() throws Exception {
        var g = group("wg-1");
        when(repository.findById("wg-1")).thenReturn(Optional.of(g));
        when(repository.save(any())).thenReturn(g);

        mockMvc.perform(put("/api/workflow-groups/wg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(g)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wg-1"));
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/workflow-groups/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(group("missing"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesGroupAndUnlinksItsWorkflows() throws Exception {
        when(workflowRepository.findAll()).thenReturn(List.of(
                workflow("wf-1", "wg-1"),
                workflow("wf-2", "other-group"),
                workflow("wf-3", null)));

        mockMvc.perform(delete("/api/workflow-groups/wg-1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById("wg-1");
        ArgumentCaptor<WorkflowDefinition> saved = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(workflowRepository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo("wf-1");
        assertThat(saved.getValue().groupId()).isNull();
    }

    @Test
    void deleteLeavesWorkflowsAloneWhenNoneLinked() throws Exception {
        when(workflowRepository.findAll()).thenReturn(List.of(workflow("wf-1", null)));

        mockMvc.perform(delete("/api/workflow-groups/wg-9"))
                .andExpect(status().isNoContent());

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void deleteUnlinksAGatedWorkflowWithoutDroppingItsGateConfiguration() throws Exception {
        var gate = new nl.metafactory.agents.approval.model.ApprovalGateConfig(true, "realisation");
        when(workflowRepository.findAll()).thenReturn(List.of(workflow("wf-1", "wg-1", gate)));

        mockMvc.perform(delete("/api/workflow-groups/wg-1"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<WorkflowDefinition> saved = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(workflowRepository).save(saved.capture());
        assertThat(saved.getValue().groupId()).isNull();
        assertThat(saved.getValue().approvalGate()).isEqualTo(gate);
    }
}
