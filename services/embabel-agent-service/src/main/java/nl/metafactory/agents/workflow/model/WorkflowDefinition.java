package nl.metafactory.agents.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;

import java.time.Instant;
import java.util.List;

public record WorkflowDefinition(
        String id,
        String name,
        String projectName,
        String groupId,
        String description,
        List<String> agentIds,
        List<String> subagentNames,
        List<String> skillNames,
        List<McpToolRef> mcpTools,
        TriggerConfig trigger,
        ExecutionConfig execution,
        boolean promptRequired,
        String promptInstructions,
        String status,
        String lastExecutionStatus,
        Instant lastExecutionAt,
        // workflow-approval-gate architecture §7.1 / ADR-007: last component, absent (null) on a
        // pre-feature YAML file (BR-34), which YamlDefinitionStore now tolerates (batch B1).
        ApprovalGateConfig approvalGate,
        // workflow-trigger-workflow-orb architecture §5.1 / ADR-008: new last component. The
        // @JsonInclude(NON_EMPTY) annotation is mandatory, not cosmetic: YamlDefinitionStore.save
        // uses a YAMLMapper whose default property inclusion is ALWAYS, so without this
        // annotation every pre-feature workflow definition re-saved after this change would gain
        // a "workflowOrbs: null" line that does not exist today, breaking byte-identical re-save
        // (AC-48) invisibly. Absence and an empty list are intentionally indistinguishable on
        // disk, which is correct: an empty orb list and no orb list mean the same thing.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<WorkflowOrb> workflowOrbs
) {

    /**
     * The three witherts below are the ADR-007 fix for risk 13: the record's fields were
     * previously re-enumerated positionally at three call sites (repository id-generation,
     * workflow-execution's post-start re-save, and group-unlink), which made it trivially easy to
     * silently drop a newly added field — most dangerously in the post-start re-save, since that
     * would make a saved gate disappear the very first time its workflow runs. A wither expressed
     * in exactly one place (here) cannot forget a component; see WorkflowDefinitionWitherTest for
     * the reflective guard that keeps this true for the *next* field too.
     */
    public WorkflowDefinition withId(String newId) {
        return new WorkflowDefinition(newId, name, projectName, groupId, description, agentIds, subagentNames,
                skillNames, mcpTools, trigger, execution, promptRequired, promptInstructions, status,
                lastExecutionStatus, lastExecutionAt, approvalGate, workflowOrbs);
    }

    public WorkflowDefinition withGroupId(String newGroupId) {
        return new WorkflowDefinition(id, name, projectName, newGroupId, description, agentIds, subagentNames,
                skillNames, mcpTools, trigger, execution, promptRequired, promptInstructions, status,
                lastExecutionStatus, lastExecutionAt, approvalGate, workflowOrbs);
    }

    public WorkflowDefinition withLastExecution(String newLastExecutionStatus, Instant newLastExecutionAt) {
        return new WorkflowDefinition(id, name, projectName, groupId, description, agentIds, subagentNames,
                skillNames, mcpTools, trigger, execution, promptRequired, promptInstructions, status,
                newLastExecutionStatus, newLastExecutionAt, approvalGate, workflowOrbs);
    }

    public WorkflowDefinition withAgentIds(List<String> newAgentIds) {
        return new WorkflowDefinition(id, name, projectName, groupId, description, newAgentIds, subagentNames, skillNames, mcpTools, trigger, execution, promptRequired, promptInstructions, status, lastExecutionStatus, lastExecutionAt, approvalGate, workflowOrbs);
    }
}
