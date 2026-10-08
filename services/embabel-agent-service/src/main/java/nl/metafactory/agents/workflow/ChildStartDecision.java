package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.WorkflowDefinition;

public sealed interface ChildStartDecision {

    record Permitted(WorkflowDefinition target) implements ChildStartDecision {}

    record Refused(String code, String reason) implements ChildStartDecision {}
}
