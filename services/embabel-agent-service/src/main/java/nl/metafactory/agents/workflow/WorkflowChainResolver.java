package nl.metafactory.agents.workflow;

import java.util.List;

import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.springframework.stereotype.Component;

@Component
public class WorkflowChainResolver {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowGroupRepository workflowGroupRepository;
    private final WorkflowTriggerProperties workflowTriggerProperties;

    public WorkflowChainResolver(WorkflowDefinitionRepository workflowDefinitionRepository,
                                 WorkflowGroupRepository workflowGroupRepository,
                                 WorkflowTriggerProperties workflowTriggerProperties) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowGroupRepository = workflowGroupRepository;
        this.workflowTriggerProperties = workflowTriggerProperties;
    }

    public ChildStartDecision resolve(String targetWorkflowId, WorkflowDefinition parent, List<String> chainAncestry) {
        var targetOpt = workflowDefinitionRepository.findById(targetWorkflowId);
        if (targetOpt.isEmpty()) {
            return new ChildStartDecision.Refused("UNKNOWN_WORKFLOW",
                    "Workflow orb target '" + targetWorkflowId + "' does not exist");
        }

        if (chainAncestry.contains(targetWorkflowId)) {
            return new ChildStartDecision.Refused("CYCLE",
                    "Starting workflow '" + targetWorkflowId + "' would create a cycle: "
                            + String.join(" -> ", chainAncestry) + " -> " + targetWorkflowId);
        }

        if ((chainAncestry.size() - 1) >= workflowTriggerProperties.getMaxChainDepth()) {
            return new ChildStartDecision.Refused("DEPTH_EXCEEDED",
                    "Workflow chain depth limit (" + workflowTriggerProperties.getMaxChainDepth()
                            + ") reached; refusing to start '" + targetWorkflowId + "' at depth " + chainAncestry.size());
        }

        WorkflowDefinition target = targetOpt.get();
        String parentProject = effectiveProject(parent);
        String targetProject = effectiveProject(target);

        if (parentProject.isEmpty() && !targetProject.isEmpty()) {
            return new ChildStartDecision.Refused("SCOPE_VIOLATION",
                    "Global workflow '" + parent.id() + "' may only reference other global workflows, "
                            + "but orb targets project-scoped workflow '" + target.id() + "' (project '" + targetProject + "')");
        }
        if (!parentProject.isEmpty() && !targetProject.isEmpty() && !targetProject.equals(parentProject)) {
            return new ChildStartDecision.Refused("SCOPE_VIOLATION",
                    "Workflow '" + parent.id() + "' (project '" + parentProject + "') may not reference workflow '"
                            + target.id() + "' (project '" + targetProject + "') — cross-project orb references are not allowed");
        }

        return new ChildStartDecision.Permitted(target);
    }

    private String effectiveProject(WorkflowDefinition d) {
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
