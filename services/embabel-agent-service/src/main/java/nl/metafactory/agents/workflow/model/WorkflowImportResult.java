package nl.metafactory.agents.workflow.model;

import java.util.List;

/**
 * The outcome of importing a workflow export bundle.
 *
 * @param groupsImported the number of workflow groups persisted from the bundle
 * @param workflowsImported the number of workflow definitions persisted from the bundle
 * @param violations human-readable workflow-orb reference violations found after persisting the
 *                   whole bundle (for example an orb referencing a workflow id present in
 *                   neither the bundle nor the pre-existing repository); these are reported, not
 *                   rejected — the import still succeeds and the referencing workflow is still
 *                   persisted. Empty when no such violation was found.
 */
public record WorkflowImportResult(
        int groupsImported,
        int workflowsImported,
        List<String> violations
) {
}
