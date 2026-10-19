package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Re-validates the workflow binding right before every start (BR-10). */
@Component
public class RunnerBindingStep {

    public sealed interface Binding {
        record Valid() implements Binding {}

        record Invalid() implements Binding {}

        record Unavailable(EmbabelRunnerClient.FailureCode code) implements Binding {}
    }

    private final EmbabelRunnerClient client;

    public RunnerBindingStep(EmbabelRunnerClient client) {
        this.client = client;
    }

    public Binding validate(String workflowId, String projectName) {
        try {
            Optional<WorkflowDefinitionDto> found = client.getWorkflow(workflowId);
            if (found.isEmpty() || found.get().promptRequired()) {
                return new Binding.Invalid();
            }
            String owner = effectiveProject(found.get());
            return owner.isEmpty() || owner.equals(projectName) ? new Binding.Valid() : new Binding.Invalid();
        } catch (UpstreamUnavailableException e) {
            return new Binding.Unavailable(e.code());
        }
    }

    private String effectiveProject(WorkflowDefinitionDto workflow) {
        if (workflow.projectName() != null && !workflow.projectName().isBlank()) {
            return workflow.projectName();
        }
        if (workflow.groupId() != null && !workflow.groupId().isBlank()) {
            return client.getWorkflowGroup(workflow.groupId())
                    .map(WorkflowGroupDto::projectName)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse("");
        }
        return "";
    }
}
