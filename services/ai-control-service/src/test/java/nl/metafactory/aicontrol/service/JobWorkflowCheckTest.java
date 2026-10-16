package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobWorkflowCheckTest {

    final EmbabelAgentClient client = mock(EmbabelAgentClient.class);
    final JobWorkflowCheck check = new JobWorkflowCheck(client);

    static WorkflowDefinitionDto workflow(List<String> agentIds) {
        var wf = mock(WorkflowDefinitionDto.class);
        when(wf.agentIds()).thenReturn(agentIds);
        return wf;
    }

    @Test
    void workflowWithLinkedAgentsPasses() {
        var wf = workflow(List.of("a2a:litellm:qa-engineer"));
        when(client.getWorkflow("wf-1")).thenReturn(Optional.of(wf));
        assertThat(check.problem("wf-1")).isEmpty();
    }

    @Test
    void unknownOrBlankWorkflowIsNotFound() {
        when(client.getWorkflow("nope")).thenReturn(Optional.empty());
        assertThat(check.problem("nope")).get().extracting(e -> e.code()).isEqualTo(GitWorkspaceJobErrorCode.WORKFLOW_NOT_FOUND);
        assertThat(check.problem(" ")).get().extracting(e -> e.code()).isEqualTo(GitWorkspaceJobErrorCode.WORKFLOW_NOT_FOUND);
    }

    @Test
    void workflowWithoutAgentsIsRejected() {
        var wf = workflow(List.of());
        when(client.getWorkflow("wf-empty")).thenReturn(Optional.of(wf));
        assertThat(check.problem("wf-empty")).get().extracting(e -> e.code())
                .isEqualTo(GitWorkspaceJobErrorCode.WORKFLOW_HAS_NO_AGENTS);
    }
}
