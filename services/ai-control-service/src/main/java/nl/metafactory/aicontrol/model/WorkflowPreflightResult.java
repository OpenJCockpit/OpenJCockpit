package nl.metafactory.aicontrol.model;

import java.util.List;

public record WorkflowPreflightResult(boolean passed, List<PreflightError> errors) {

    public static WorkflowPreflightResult ok() {
        return new WorkflowPreflightResult(true, List.of());
    }

    public static WorkflowPreflightResult failed(List<PreflightError> errors) {
        return new WorkflowPreflightResult(false, errors);
    }
}
