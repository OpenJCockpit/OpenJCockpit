package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.agents.AgentRef;
import nl.metafactory.agents.workflow.agents.AgentReferenceCheck;

import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Centralises {@link WorkflowDefinition} write-boundary validation, extracted from
 * {@code WorkflowController} so the reconciler (batch B2) can reuse the non-throwing
 * {@link #gatePlacementViolation(WorkflowDefinition)} check before persisting a repaired
 * record, without duplicating the throwing rule used by the write endpoints.
 */
@Component
public class WorkflowDefinitionValidator {

    private final WorkflowOrbValidator orbValidator;
    private final AgentReferenceCheck agentReferenceCheck;

    public WorkflowDefinitionValidator(WorkflowOrbValidator orbValidator, AgentReferenceCheck agentReferenceCheck) {
        this.orbValidator = orbValidator;
        this.agentReferenceCheck = agentReferenceCheck;
    }

    public void validateForWrite(WorkflowDefinition definition) {
        requireGroupOrProject(definition);
        requireAtLeastOneAgent(definition);
        requireCatalogAgentReferences(definition, true);
        validateApprovalGate(definition);
        orbValidator.validateForWrite(definition);
    }

    /**
     * Runs only the three pre-existing structural checks (group-or-project, at-least-one-agent,
     * approval-gate placement), deliberately WITHOUT the workflow-orb checks that
     * {@link #validateForWrite(WorkflowDefinition)} also performs. The bulk
     * {@code /api/workflows/import} endpoint uses this instead of {@link #validateForWrite}
     * because a bundle may contain forward references between its own members (workflow A's orb
     * pointing at workflow B, both arriving in the same bundle) that cannot resolve against the
     * repository until the whole bundle has been persisted. The import endpoint therefore calls
     * this method before persisting anything (to keep rejecting structurally invalid definitions,
     * unchanged from today), then separately calls {@code WorkflowOrbValidator.orbViolations}
     * after persisting the whole bundle to report (not reject) any orb reference that remains
     * unresolved even then.
     */
    public void validateStructuralRulesForImport(WorkflowDefinition definition) {
        requireGroupOrProject(definition);
        requireAtLeastOneAgent(definition);
        requireCatalogAgentReferences(definition, false);
        validateApprovalGate(definition);
    }

    /**
     * Every entry of {@code agentIds} must reference an agent of the central catalog
     * ({@code local:<id>} or {@code a2a:<provider>:<id>}); built-in pipeline stage ids are no longer
     * accepted for workflows. On a write the referenced agent must also exist (when verifiable).
     */
    private void requireCatalogAgentReferences(WorkflowDefinition definition, boolean checkExistence) {
        for (String key : definition.agentIds()) {
            var ref = AgentRef.parse(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + key + "' is not an agent reference. Link agents from Workflow design → Agents "
                            + "(local:<id> or a2a:<provider>:<id>) in workflow '" + definition.id() + "'."));
            if (checkExistence) {
                agentReferenceCheck.problem(ref).ifPresent(problem -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
                });
            }
        }
        if (definition.agentIds().stream().distinct().count() != definition.agentIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An agent can be linked only once per workflow ('" + definition.id() + "').");
        }
    }

    private void requireGroupOrProject(WorkflowDefinition definition) {
        boolean hasGroup = definition.groupId() != null && !definition.groupId().isBlank();
        boolean hasProject = definition.projectName() != null && !definition.projectName().isBlank();
        if (!hasGroup && !hasProject) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A workflow without a group must be linked to a project: fill in groupId or projectName"
                    + (definition.id() != null && !definition.id().isBlank() ? " (workflow " + definition.id() + ")" : ""));
        }
    }

    private void requireAtLeastOneAgent(WorkflowDefinition definition) {
        if (definition.agentIds() == null || definition.agentIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one agent must be linked to workflow '" + definition.id() + "'.");
        }
    }

    private void validateApprovalGate(WorkflowDefinition definition) {
        var gate = definition.approvalGate();
        if (gate == null || !gate.enabled()) {
            return;
        }
        List<String> agentIds = definition.agentIds() != null ? definition.agentIds() : List.of();
        if (gate.placementStage() == null || !agentIds.contains(gate.placementStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Approval gate placement stage '" + gate.placementStage()
                            + "' is not among this workflow's selected agents: " + agentIds);
        }
    }

    public Optional<String> gatePlacementViolation(WorkflowDefinition definition) {
        var gate = definition.approvalGate();
        if (gate == null || !gate.enabled()) {
            return Optional.empty();
        }
        List<String> agentIds = definition.agentIds() != null ? definition.agentIds() : List.of();
        if (gate.placementStage() == null || !agentIds.contains(gate.placementStage())) {
            return Optional.of("Approval gate placement stage '" + gate.placementStage()
                    + "' is not among this workflow's selected agents: " + agentIds);
        }
        return Optional.empty();
    }
}
