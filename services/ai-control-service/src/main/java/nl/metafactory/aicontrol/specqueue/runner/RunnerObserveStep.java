package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.planner.RunOutcome;
import org.springframework.stereotype.Component;

/** Reads a running item's run once per tick and classifies its end state. */
@Component
public class RunnerObserveStep {

    private final SpecQueueRunnerTransitions transitions;
    private final EmbabelRunnerClient client;
    private final RunOutcomeClassifier classifier;
    private final ProjectRepository projects;

    public RunnerObserveStep(SpecQueueRunnerTransitions transitions, EmbabelRunnerClient client,
                             RunOutcomeClassifier classifier, ProjectRepository projects) {
        this.transitions = transitions;
        this.client = client;
        this.classifier = classifier;
        this.projects = projects;
    }

    public void observeRunning(SpecQueueItem item) {
        var projectId = item.getProjectId();
        String runId = item.getWorkflowRunId();
        if (runId == null || runId.isBlank()) {
            transitions.failActiveItem(projectId, item.getId(), SpecQueueItemStatus.RUNNING,
                    SpecQueueFailureReason.RUN_STATE_LOST, null);
            return;
        }
        var lookup = client.getRun(runId);
        if (lookup instanceof EmbabelRunnerClient.RunLookup.Unavailable unavailable) {
            // An outage never fails an item, and a 404 is never "lost run".
            transitions.recordPollError(projectId, RunnerPollErrorCodes.forEmbabel(unavailable.code()));
            return;
        }
        var run = ((EmbabelRunnerClient.RunLookup.Found) lookup).run();
        var project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project of an active item is missing"));
        RunOutcome outcome = classifier.classify(run, item.getCancelRequestedAt() != null, project.getGitUrl());
        String status = classifier.normalizeStatus(run.status());
        switch (outcome) {
            case RunOutcome.NonTerminal nonTerminal ->
                    transitions.recordObservation(projectId, item.getId(), status);
            case RunOutcome.CancelledByQueue cancelled -> transitions.cancelRunByQueue(projectId, item.getId());
            default -> transitions.finishRun(projectId, item.getId(), outcome, classifier.features(run), status);
        }
    }
}
