package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.workflowrun.WorkflowRunScope;

import java.util.Optional;

/**
 * Called after each agent of a run finished, before the next one starts. For a run bound to a git
 * workspace, the checkpoint commits and pushes the run's branch, so the work of finished agents survives
 * a later failure. Never throws: a failed checkpoint is reported, not fatal (the job's final push retries).
 */
public interface RunCheckpoints {

    RunCheckpoints NONE = (scope, agentKey, position, total, output) -> Optional.empty();

    /** A short, loggable outcome for the run's events; empty when there was nothing to checkpoint. */
    Optional<String> afterAgent(WorkflowRunScope scope, String agentKey, int position, int total, String output);
}
