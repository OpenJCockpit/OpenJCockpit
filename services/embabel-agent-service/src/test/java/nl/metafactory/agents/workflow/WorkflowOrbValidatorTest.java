package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One test per {@link WorkflowOrbValidator} rule (BR-1, BR-7, BR-8, BR-9, BR-10, BR-12,
 * BR-14/15/16), plus {@code referencedBy} coverage for the delete guard (BR-13/AC-08).
 */
class WorkflowOrbValidatorTest {

    private WorkflowDefinition workflow(String id, String projectName, String groupId,
                                         List<String> agentIds, List<WorkflowOrb> orbs) {
        return new WorkflowDefinition(id, "Name " + id, projectName, groupId, "desc",
                agentIds, List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE",
                null, null, null, orbs);
    }

    @Test
    void capExceededUsesConfiguredMaximumNotALiteral() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-t1")).thenReturn(Optional.of(workflow("wf-t1", "", null, List.of(), List.of())));
        when(repo.findById("wf-t2")).thenReturn(Optional.of(workflow("wf-t2", "", null, List.of(), List.of())));
        when(repo.findById("wf-t3")).thenReturn(Optional.of(workflow("wf-t3", "", null, List.of(), List.of())));

        var orbs = List.of(
                new WorkflowOrb("wf-t1", WorkflowOrbMode.SEQUENTIAL, null),
                new WorkflowOrb("wf-t2", WorkflowOrbMode.SEQUENTIAL, null),
                new WorkflowOrb("wf-t3", WorkflowOrbMode.SEQUENTIAL, null));
        var d = workflow("wf-cap", "", null, List.of(), orbs);

        var lowCapProps = new WorkflowTriggerProperties();
        lowCapProps.setMaxOrbsPerWorkflow(2);
        var lowCapValidator = new WorkflowOrbValidator(repo, groupRepo, lowCapProps);

        assertThatThrownBy(() -> lowCapValidator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("exceeding the configured maximum of 2");

        var highCapProps = new WorkflowTriggerProperties();
        highCapProps.setMaxOrbsPerWorkflow(5);
        var highCapValidator = new WorkflowOrbValidator(repo, groupRepo, highCapProps);

        var violations = highCapValidator.orbViolations(d);
        assertThat(violations).noneMatch(v -> v.contains("exceeding the configured maximum"));
    }

    @Test
    void selfReferenceIsRejected() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-self", "", null, List.of(),
                List.of(new WorkflowOrb("wf-self", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot reference itself");
    }

    @Test
    void modeNullIsRejected() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-mode", "", null, List.of(),
                List.of(new WorkflowOrb("wf-other", null, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must specify a mode");
    }

    @Test
    void anchorNotInAgentIdsIsRejectedWithApprovalGateStyleMessage() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target")).thenReturn(Optional.of(workflow("wf-target", "", null, List.of(), List.of())));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-anchor", "", null, List.of("requirement"),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, "not-a-real-stage")));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("400 BAD_REQUEST \"Workflow orb placement stage 'not-a-real-stage' is not among this workflow's selected agents: [requirement]\"");
    }

    @Test
    void unknownTargetWorkflowIsRejected() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-missing")).thenReturn(Optional.empty());
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-unknown-target", "", null, List.of(),
                List.of(new WorkflowOrb("wf-missing", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("references unknown workflow")
                .hasMessageContaining("wf-missing");
    }

    @Test
    void cycleAcrossTwoStoredDefinitionsIsDetectedAndNamesThePath() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);

        var wfA = workflow("wf-a", "", null, List.of(),
                List.of(new WorkflowOrb("wf-b", WorkflowOrbMode.SEQUENTIAL, null)));
        var wfB = workflow("wf-b", "", null, List.of(),
                List.of(new WorkflowOrb("wf-a", WorkflowOrbMode.SEQUENTIAL, null)));

        when(repo.findAll()).thenReturn(List.of(wfA, wfB));
        when(repo.findById("wf-a")).thenReturn(Optional.of(wfA));
        when(repo.findById("wf-b")).thenReturn(Optional.of(wfB));

        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        assertThatThrownBy(() -> validator.validateForWrite(wfA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cycle")
                .hasMessageContaining("wf-a")
                .hasMessageContaining("wf-b");
    }

    @Test
    void fanOutCycleReportsOnlyTheActualCyclePathNotEveryDiscoveredNode() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);

        var wfA = workflow("wf-a", "", null, List.of(),
                List.of(new WorkflowOrb("wf-b", WorkflowOrbMode.SEQUENTIAL, null),
                        new WorkflowOrb("wf-c", WorkflowOrbMode.SEQUENTIAL, null)));
        var wfB = workflow("wf-b", "", null, List.of(), List.of());
        var wfC = workflow("wf-c", "", null, List.of(),
                List.of(new WorkflowOrb("wf-a", WorkflowOrbMode.SEQUENTIAL, null)));

        when(repo.findAll()).thenReturn(List.of(wfA, wfB, wfC));
        when(repo.findById("wf-a")).thenReturn(Optional.of(wfA));
        when(repo.findById("wf-b")).thenReturn(Optional.of(wfB));
        when(repo.findById("wf-c")).thenReturn(Optional.of(wfC));

        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        assertThatThrownBy(() -> validator.validateForWrite(wfA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cycle")
                .hasMessageContaining("wf-a")
                .hasMessageContaining("wf-c")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getReason()).doesNotContain("wf-b"));
    }

    @Test
    void crossProjectReferenceIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectB", null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-parent", "ProjectA", null, List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cross-project orb references are not allowed")
                .hasMessageContaining("ProjectA")
                .hasMessageContaining("ProjectB");
    }

    @Test
    void globalWorkflowReferencingProjectScopedWorkflowIsRefused() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectC", null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-global-parent", null, null, List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("may only reference other global workflows")
                .hasMessageContaining("ProjectC");
    }

    @Test
    void sameProjectReferenceIsAllowed() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "ProjectA", null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-parent", "ProjectA", null, List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThat(validator.orbViolations(d)).isEmpty();
    }

    @Test
    void globalWorkflowMayBeReferencedByAProjectScopedWorkflow() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-global-target", null, null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-global-target")).thenReturn(Optional.of(target));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-parent", "ProjectA", null, List.of(),
                List.of(new WorkflowOrb("wf-global-target", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThat(validator.orbViolations(d)).isEmpty();
    }

    @Test
    void effectiveProjectFallsBackToGroupProjectNameWhenOwnProjectNameIsBlank() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var target = workflow("wf-target", "OtherProject", null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target")).thenReturn(Optional.of(target));
        when(groupRepo.findById("wg-1")).thenReturn(Optional.of(
                new WorkflowGroup("wg-1", "Group One", "desc", "GroupProject")));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-parent", null, "wg-1", List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(d))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("GroupProject");
    }

    @Test
    void effectiveProjectPrefersOwnProjectNameOverGroupProjectName() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(groupRepo.findById("wg-1")).thenReturn(Optional.of(
                new WorkflowGroup("wg-1", "Group One", "desc", "UnrelatedGroupProject")));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var sameProjectTarget = workflow("wf-target-own", "OwnProject", null, List.of(), List.of());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-target-own")).thenReturn(Optional.of(sameProjectTarget));

        var dOwn = workflow("wf-parent", "OwnProject", "wg-1", List.of(),
                List.of(new WorkflowOrb("wf-target-own", WorkflowOrbMode.SEQUENTIAL, null)));
        assertThat(validator.orbViolations(dOwn)).isEmpty();

        var groupProjectTarget = workflow("wf-target-unrelated", "UnrelatedGroupProject", null, List.of(), List.of());
        when(repo.findById("wf-target-unrelated")).thenReturn(Optional.of(groupProjectTarget));

        var dUnrelated = workflow("wf-parent2", "OwnProject", "wg-1", List.of(),
                List.of(new WorkflowOrb("wf-target-unrelated", WorkflowOrbMode.SEQUENTIAL, null)));
        assertThat(validator.orbViolations(dUnrelated)).isNotEmpty();
    }

    @Test
    void orbWithNullWorkflowIdIsSkippedByTheScopeCheck() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var d = workflow("wf-null-target-id", "", null, List.of(),
                List.of(new WorkflowOrb(null, WorkflowOrbMode.SEQUENTIAL, null)));

        assertThat(validator.orbViolations(d)).isEmpty();
    }

    @Test
    void cycleDetectionBudgetExhaustionStopsMidLoopWithoutError() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById("wf-y1")).thenReturn(Optional.of(workflow("wf-y1", "", null, List.of(), List.of())));
        when(repo.findById("wf-y2")).thenReturn(Optional.of(workflow("wf-y2", "", null, List.of(), List.of())));

        var props = new WorkflowTriggerProperties();
        props.setMaxOrbsPerWorkflow(1);
        props.setMaxChainDepth(1);
        var validator = new WorkflowOrbValidator(repo, groupRepo, props);

        var d = workflow("wf-x", "", null, List.of(),
                List.of(new WorkflowOrb("wf-y1", WorkflowOrbMode.SEQUENTIAL, null),
                        new WorkflowOrb("wf-y2", WorkflowOrbMode.SEQUENTIAL, null)));

        var violations = validator.orbViolations(d);

        assertThat(violations).anyMatch(v -> v.contains("exceeding the configured maximum of 1"));
    }

    @Test
    void referencedByReturnsWorkflowsThatReferenceTheGivenId() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var referrer1 = workflow("wf-referrer-1", "", null, List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));
        var referrer2 = workflow("wf-referrer-2", "", null, List.of(),
                List.of(new WorkflowOrb("wf-target", WorkflowOrbMode.SEQUENTIAL, null)));
        var unrelated = workflow("wf-unrelated", "", null, List.of(),
                List.of(new WorkflowOrb("wf-something-else", WorkflowOrbMode.SEQUENTIAL, null)));
        when(repo.findAll()).thenReturn(List.of(referrer1, referrer2, unrelated));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        var result = validator.referencedBy("wf-target");

        assertThat(result).containsExactlyInAnyOrder("wf-referrer-1", "wf-referrer-2");
        assertThat(result).doesNotContain("wf-unrelated");
    }

    @Test
    void referencedByExcludesTheDefinitionsOwnSelfReference() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var groupRepo = mock(WorkflowGroupRepository.class);
        var selfReferencing = workflow("wf-self", "", null, List.of(),
                List.of(new WorkflowOrb("wf-self", WorkflowOrbMode.SEQUENTIAL, null)));
        when(repo.findAll()).thenReturn(List.of(selfReferencing));
        var validator = new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties());

        assertThat(validator.referencedBy("wf-self")).isEmpty();
    }
}
