package nl.metafactory.agents.workflow.model;

/**
 * Logical group that bundles workflows with a shared meaning (for example
 * "onboarding" or "compliance") in the designer. A group
 * without a projectName is global: its workflows are available to every
 * project (like the default "Spec-driven development" group).
 */
public record WorkflowGroup(
        String id,
        String name,
        String description,
        String projectName
) {
}
