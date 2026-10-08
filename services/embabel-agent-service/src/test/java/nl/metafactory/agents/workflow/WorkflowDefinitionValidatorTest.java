package nl.metafactory.agents.workflow;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionValidatorTest {

    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(new WorkflowOrbValidator(null, null, new WorkflowTriggerProperties()));

    private WorkflowDefinition workflow(String groupId, String projectName, List<String> agentIds,
                                         ApprovalGateConfig gate) {
        return new WorkflowDefinition("wf-1", "Onboarding", projectName, groupId, "desc",
                agentIds, List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, gate, null);
    }

    @Test
    void validateForWriteThrowsWhenGroupAndProjectBothMissing() {
        var invalid = workflow(null, null, List.of("requirement"), null);

        assertThatThrownBy(() -> validator.validateForWrite(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A workflow without a group must be linked to a project");
    }

    @Test
    void validateForWritePassesWhenGroupPresentAndProjectMissing() {
        var valid = workflow("wg-1", null, List.of("requirement"), null);

        validator.validateForWrite(valid);
    }

    @Test
    void validateForWriteThrowsWhenGatePlacementStageNotSelected() {
        var invalid = workflow("wg-1", null, List.of("requirement"),
                new ApprovalGateConfig(true, "realisation"));

        assertThatThrownBy(() -> validator.validateForWrite(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Approval gate placement stage 'realisation' is not among this workflow's selected agents");
    }

    @Test
    void validateForWritePassesWhenGateDisabledEvenIfPlacementStale() {
        var valid = workflow("wg-1", null, List.of("requirement"),
                new ApprovalGateConfig(false, "realisation"));

        validator.validateForWrite(valid);
    }

    @Test
    void validateForWritePassesWhenGatePlacementStageIsSelected() {
        var valid = workflow("wg-1", null, List.of("requirement", "impact"),
                new ApprovalGateConfig(true, "impact"));

        validator.validateForWrite(valid);
    }

    @Test
    void gatePlacementViolationReturnsEmptyWhenGateIsNull() {
        var definition = workflow("wg-1", null, List.of("requirement"), null);

        assertThat(validator.gatePlacementViolation(definition)).isEmpty();
    }

    @Test
    void gatePlacementViolationReturnsEmptyWhenGateDisabled() {
        var definition = workflow("wg-1", null, List.of("requirement"),
                new ApprovalGateConfig(false, "realisation"));

        assertThat(validator.gatePlacementViolation(definition)).isEmpty();
    }

    @Test
    void gatePlacementViolationReturnsMessageWhenPlacementStageNotSelected() {
        var definition = workflow("wg-1", null, List.of("requirement"),
                new ApprovalGateConfig(true, "realisation"));

        Optional<String> violation = validator.gatePlacementViolation(definition);

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("Approval gate placement stage 'realisation'");
    }

    @Test
    void gatePlacementViolationReturnsEmptyWhenPlacementStageIsSelected() {
        var definition = workflow("wg-1", null, List.of("requirement", "impact"),
                new ApprovalGateConfig(true, "impact"));

        assertThat(validator.gatePlacementViolation(definition)).isEmpty();
    }

    @Test
    void gatePlacementViolationTreatsNullAgentIdsAsEmptyList() {
        var definition = workflow("wg-1", null, null, new ApprovalGateConfig(true, "realisation"));

        Optional<String> violation = validator.gatePlacementViolation(definition);

        assertThat(violation).isPresent();
    }

    @Test
    void validateForWriteThrowsWhenAgentIdsIsEmpty() {
        var invalid = workflow("wg-1", null, List.of(), null);

        assertThatThrownBy(() -> validator.validateForWrite(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At least one pipeline agent must be selected for workflow 'wf-1'.");
    }

    @Test
    void validateForWriteThrowsWhenAgentIdsIsNull() {
        var invalid = workflow("wg-1", null, null, null);

        assertThatThrownBy(() -> validator.validateForWrite(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At least one pipeline agent must be selected for workflow 'wf-1'.");
    }

    @Test
    void validateForWritePassesWhenAtLeastOneAgentIsSelected() {
        var valid = workflow("wg-1", null, List.of("requirement"), null);

        validator.validateForWrite(valid);
    }

    @Test
    void requireAtLeastOneAgentStillFiresWithItsUnchangedMessageForAnOrbsOnlyAgentLessWorkflow() {
        var orbsOnlyNoAgents = new WorkflowDefinition("wf-1", "Onboarding", null, "wg-1", "desc",
                List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null,
                List.of(new nl.metafactory.agents.workflow.model.WorkflowOrb("wf-other",
                        nl.metafactory.agents.workflow.model.WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validator.validateForWrite(orbsOnlyNoAgents))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At least one pipeline agent must be selected for workflow 'wf-1'.");
    }

    @Test
    void validateForWriteDelegatesToOrbValidatorAndRejectsASelfReferencingOrb() {
        var repo = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        var groupRepo = org.mockito.Mockito.mock(WorkflowGroupRepository.class);
        org.mockito.Mockito.when(repo.findAll()).thenReturn(List.of());
        var validatorWithOrbChecks = new WorkflowDefinitionValidator(
                new WorkflowOrbValidator(repo, groupRepo, new WorkflowTriggerProperties()));

        var selfReferencing = new WorkflowDefinition("wf-1", "Onboarding", null, "wg-1", "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null,
                List.of(new nl.metafactory.agents.workflow.model.WorkflowOrb("wf-1",
                        nl.metafactory.agents.workflow.model.WorkflowOrbMode.SEQUENTIAL, null)));

        assertThatThrownBy(() -> validatorWithOrbChecks.validateForWrite(selfReferencing))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot reference itself");
    }
}
