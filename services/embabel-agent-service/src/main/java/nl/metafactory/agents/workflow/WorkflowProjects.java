package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;

final class WorkflowProjects {

    private WorkflowProjects() {
    }

    /** The workflow's own project name, falling back to its group's; empty when neither is set. */
    static String effectiveProject(WorkflowDefinition d, WorkflowGroupRepository workflowGroupRepository) {
        if (d.projectName() != null && !d.projectName().isBlank()) {
            return d.projectName();
        }
        if (d.groupId() != null && !d.groupId().isBlank()) {
            WorkflowGroup group = workflowGroupRepository.findById(d.groupId()).orElse(null);
            if (group != null && group.projectName() != null && !group.projectName().isBlank()) {
                return group.projectName();
            }
        }
        return "";
    }
}
