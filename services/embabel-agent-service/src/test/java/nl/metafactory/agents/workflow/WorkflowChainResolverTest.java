package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One test per {@link WorkflowChainResolver#resolve} outcome (UNKNOWN_WORKFLOW, CYCLE,
 * DEPTH_EXCEEDED, SCOPE_VIOLATION, Permitted).
 */
class WorkflowChainResolverTest {

    private WorkflowDefinition workflow(String id, String projectName, String groupId) {
        return new WorkflowDefinition(id, "Name " + id, projectName, groupId, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE",
                null, null, null, List.of());
    }

    @Test
    void unknownTargetWorkflowIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findById("wf-missing")).thenReturn(Optional.empty());
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-parent", "", null);
        var decision = resolver.resolve("wf-missing", parent, List.of("wf-parent"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("UNKNOWN_WORKFLOW");
        assertThat(refused.reason()).contains("wf-missing");
    }

    @Test
    void cycleViaAncestryIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-a", "", null);
        when(repo.findById("wf-a")).thenReturn(Optional.of(target));
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-c", "", null);
        var decision = resolver.resolve("wf-a", parent, List.of("wf-a", "wf-b", "wf-c"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("CYCLE");
        assertThat(refused.reason()).contains("wf-a").contains("wf-b").contains("wf-c");
    }

    @Test
    void depthExceededViaAncestrySizeIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-deep", "", null);
        when(repo.findById("wf-deep")).thenReturn(Optional.of(target));
        var props = new WorkflowTriggerProperties();
        props.setMaxChainDepth(2);
        var resolver = new WorkflowChainResolver(repo, groupRepo, props);

        var parent = workflow("wf-level2", "", null);
        var decision = resolver.resolve("wf-deep", parent, List.of("wf-root", "wf-level1", "wf-level2"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("DEPTH_EXCEEDED");
        assertThat(refused.reason()).contains("2");
    }

    @Test
    void scopeViolationIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectB", null);
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-parent", "ProjectA", null);
        var decision = resolver.resolve("wf-target", parent, List.of("wf-parent"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("SCOPE_VIOLATION");
        assertThat(refused.reason()).contains("ProjectA").contains("ProjectB");
    }

    @Test
    void globalParentReferencingProjectScopedTargetIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectC", null);
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-global-parent", null, null);
        var decision = resolver.resolve("wf-target", parent, List.of("wf-global-parent"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("SCOPE_VIOLATION");
        assertThat(refused.reason()).contains("may only reference other global workflows").contains("ProjectC");
    }

    @Test
    void happyPathReturnsPermittedWithTheResolvedTarget() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectA", null);
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-parent", "ProjectA", null);
        var decision = resolver.resolve("wf-target", parent, List.of("wf-parent"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Permitted.class);
        var permitted = (ChildStartDecision.Permitted) decision;
        assertThat(permitted.target()).isEqualTo(target);
    }

    @Test
    void effectiveProjectFallsBackToGroupProjectNameForScopeCheck() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "OtherProject", null);
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        when(groupRepo.findById("wg-1")).thenReturn(Optional.of(
                new WorkflowGroup("wg-1", "Group One", "desc", "GroupProject")));
        var resolver = new WorkflowChainResolver(repo, groupRepo, new WorkflowTriggerProperties());

        var parent = workflow("wf-parent", null, "wg-1");
        var decision = resolver.resolve("wf-target", parent, List.of("wf-parent"));

        assertThat(decision).isInstanceOf(ChildStartDecision.Refused.class);
        var refused = (ChildStartDecision.Refused) decision;
        assertThat(refused.code()).isEqualTo("SCOPE_VIOLATION");
        assertThat(refused.reason()).contains("GroupProject");
    }
}
