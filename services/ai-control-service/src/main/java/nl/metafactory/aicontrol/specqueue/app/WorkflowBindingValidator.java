package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Checks that a workflow exists, is global or owned by the project, and needs no prompt. */
@Component
public class WorkflowBindingValidator {

    private static final int MAX_NAME = 255;

    public record ValidatedWorkflow(String workflowId, String workflowName) {}

    private final EmbabelAgentClient client;

    public WorkflowBindingValidator(EmbabelAgentClient client) {
        this.client = client;
    }

    public ValidatedWorkflow validate(String workflowId, String projectName) {
        try {
            WorkflowDefinitionDto workflow = client.getWorkflowStrict(workflowId)
                    .orElseThrow(() -> new SpecQueueException(Code.WORKFLOW_NOT_FOUND, "Workflow not found"));
            String owner = effectiveProject(workflow);
            if (!owner.isEmpty() && !owner.equals(projectName)) {
                throw new SpecQueueException(Code.WORKFLOW_NOT_ALLOWED_FOR_PROJECT,
                        "Workflow belongs to a different project");
            }
            if (workflow.promptRequired()) {
                throw new SpecQueueException(Code.WORKFLOW_PROMPT_REQUIRED,
                        "Workflows that require a prompt cannot be queued");
            }
            String name = workflow.name();
            if (name != null && name.length() > MAX_NAME) {
                name = name.substring(0, MAX_NAME);
            }
            return new ValidatedWorkflow(workflowId, name);
        } catch (ResponseStatusException e) {
            throw new SpecQueueException(Code.EMBABEL_UNAVAILABLE, "The agent service is unavailable");
        }
    }

    private String effectiveProject(WorkflowDefinitionDto workflow) {
        if (workflow.projectName() != null && !workflow.projectName().isBlank()) {
            return workflow.projectName();
        }
        if (workflow.groupId() != null && !workflow.groupId().isBlank()) {
            Optional<WorkflowGroupDto> group = client.getWorkflowGroupStrict(workflow.groupId());
            return group.map(WorkflowGroupDto::projectName).filter(n -> !n.isBlank()).orElse("");
        }
        return "";
    }
}
