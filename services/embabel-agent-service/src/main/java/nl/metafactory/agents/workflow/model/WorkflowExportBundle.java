package nl.metafactory.agents.workflow.model;

import java.util.List;

/** JSON format in which workflows and their groups are exported and imported together. */
public record WorkflowExportBundle(
        List<WorkflowGroup> groups,
        List<WorkflowDefinition> workflows
) {
}
