package nl.metafactory.agentruntime.workflowrun;

import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.common.agentruntime.WorkflowRunDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowRunScopeResolverTest {

    private final AgentServiceClient agentService = mock(AgentServiceClient.class);
    private final WorkflowRunScopeResolver resolver = new WorkflowRunScopeResolver(agentService);

    @Test
    void anActiveRunIsResolvedWithItsValidatedGitWorkspace() {
        when(agentService.workflowRun("run-1")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf-1", "run-1",
                new WorkflowRunDescriptor.GitWorkspace("job-1", "https://git.example/r.git", "feature/x"))));
        when(agentService.workflowRun("run-2")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf-2", "run-2", null)));

        var scope = resolver.find("run-1").orElseThrow();
        assertThat(scope.workflowId()).isEqualTo("wf-1");
        assertThat(scope.gitWorkspace().branch()).isEqualTo("feature/x");
        assertThat(resolver.find("run-2").orElseThrow().gitWorkspace()).isNull();
    }

    @Test
    void noRunIdOrAnUnknownRunHasNoScope() {
        when(agentService.workflowRun("gone")).thenReturn(Optional.empty());
        assertThat(resolver.find("gone")).isEmpty();
        assertThat(resolver.find(null)).isEmpty();
        assertThat(resolver.find(" ")).isEmpty();
    }

    @Test
    void anInvalidOrMismatchedDescriptorIsRejectedNotPassedOn() {
        when(agentService.workflowRun("run-1")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf", "run-1",
                new WorkflowRunDescriptor.GitWorkspace("../escape", "https://git.example/r.git", "b"))));
        when(agentService.workflowRun("run-3")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf", "run-other", null)));
        when(agentService.workflowRun("run-4")).thenReturn(Optional.of(new WorkflowRunDescriptor("wf", null, null)));

        for (String run : new String[]{"run-1", "run-3", "run-4"}) {
            assertThatThrownBy(() -> resolver.find(run)).as(run)
                    .isInstanceOf(AgentServiceClient.ManagementUnavailableException.class);
        }
    }

    @Test
    void anUnreachableEngineIsReportedToTheCaller() {
        var resolverOfNothing = new WorkflowRunScopeResolver(mock(AgentServiceClient.class));
        assertThat(resolverOfNothing.find("x")).isEmpty();
        when(agentService.workflowRun("run-1")).thenThrow(new AgentServiceClient.ManagementUnavailableException("down", null));
        assertThatThrownBy(() -> resolver.find("run-1")).isInstanceOf(AgentServiceClient.ManagementUnavailableException.class);
    }
}
