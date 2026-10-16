package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.PreflightError;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Job preflight: the workflow a git-workspace job runs must exist and have catalog agents linked. */
@Component
public class JobWorkflowCheck {

    private final EmbabelAgentClient agentClient;

    public JobWorkflowCheck(EmbabelAgentClient agentClient) {
        this.agentClient = agentClient;
    }

    public Optional<PreflightError> problem(String workflowId) {
        var workflow = workflowId == null || workflowId.isBlank() ? Optional.<nl.metafactory.aicontrol.client.WorkflowDefinitionDto>empty()
                : agentClient.getWorkflow(workflowId);
        if (workflow.isEmpty()) {
            return Optional.of(new PreflightError(GitWorkspaceJobErrorCode.WORKFLOW_NOT_FOUND,
                    "Workflow '" + workflowId + "' does not exist"));
        }
        var agents = workflow.get().agentIds();
        if (agents == null || agents.isEmpty()) {
            return Optional.of(new PreflightError(GitWorkspaceJobErrorCode.WORKFLOW_HAS_NO_AGENTS,
                    "Workflow '" + workflowId + "' has no agents linked; link agents in Workflow design"));
        }
        return Optional.empty();
    }
}
