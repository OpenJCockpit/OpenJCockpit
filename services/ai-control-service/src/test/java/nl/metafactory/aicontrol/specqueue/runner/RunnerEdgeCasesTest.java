package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import nl.metafactory.aicontrol.service.WorkflowStartEnrichmentService;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.github.PullRequestUrlParser;
import nl.metafactory.aicontrol.specqueue.planner.FifoQueuePlanner;
import nl.metafactory.aicontrol.specqueue.planner.PlannerGuardrails;
import nl.metafactory.aicontrol.specqueue.planner.PlannerItemView;
import nl.metafactory.aicontrol.specqueue.planner.RunFinishedDecision;
import nl.metafactory.aicontrol.specqueue.planner.RunOutcome;
import nl.metafactory.aicontrol.specqueue.planner.PlannerOverride;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.FailureCode;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.NotSentReason;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.RunLookup;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.StartResult;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.StartClaim;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Refusal, error and fallback branches of the runner collaborators that the flow tests never reach. */
class RunnerEdgeCasesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID projectId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    // ---- RunnerStartStep

    private final SpecQueueRunnerTransitions transitions = mock(SpecQueueRunnerTransitions.class);
    private final RunnerBindingStep binding = mock(RunnerBindingStep.class);
    private final EmbabelRunnerClient client = mock(EmbabelRunnerClient.class);
    private final WorkflowPreflightService preflight = mock(WorkflowPreflightService.class);
    private final WorkflowStartEnrichmentService enrichment = mock(WorkflowStartEnrichmentService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);

    @SuppressWarnings("unchecked")
    private RunnerStartStep startStep() {
        ObjectProvider<Clock> clock = mock(ObjectProvider.class);
        when(clock.getIfAvailable(any())).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        var project = mock(Project.class);
        when(project.getName()).thenReturn("proj");
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(transitions.claimNextItem(projectId)).thenReturn(Optional.of(new StartClaim(projectId, itemId, "wf", "a.md")));
        return new RunnerStartStep(transitions, binding, client, preflight, enrichment, projects,
                new SpecQueueProperties(), clock);
    }

    @Test
    void anInvalidWorkflowBindingFailsTheStartWithoutSendingAnything() {
        var step = startStep();
        when(binding.validate("wf", "proj")).thenReturn(new RunnerBindingStep.Binding.Invalid());
        step.startNextItem(projectId);
        verify(transitions).failStart(projectId, itemId, SpecQueueFailureReason.WORKFLOW_INVALID, SpecQueueEventType.FAILED);
        verify(client, never()).startWorkflow(any(), any());
    }

    @Test
    void anUnavailableWorkflowLookupRevertsTheStartWithAPollError() {
        var step = startStep();
        when(binding.validate("wf", "proj")).thenReturn(new RunnerBindingStep.Binding.Unavailable(FailureCode.UNAUTHORIZED));
        step.startNextItem(projectId);
        verify(transitions).revertStart(projectId, itemId, RunnerPollErrorCodes.EMBABEL_UNAUTHORIZED);
        verify(client, never()).startWorkflow(any(), any());
    }

    @Test
    void aRunThatCouldNotBeBoundToTheItemIsRecordedAsOrphan() {
        var step = startStep();
        when(binding.validate("wf", "proj")).thenReturn(new RunnerBindingStep.Binding.Valid());
        when(preflight.validateBeforeWorkflowStart(projectId))
                .thenReturn(nl.metafactory.aicontrol.model.WorkflowPreflightResult.ok());
        when(enrichment.enrich(any())).thenReturn(WorkflowStartInputDto.empty());
        when(client.startWorkflow(eq("wf"), any())).thenReturn(new StartResult.Started("run-9", "RUNNING", NOW));

        when(transitions.recordStarted(projectId, itemId, "run-9", "RUNNING", NOW)).thenReturn(false);
        step.startNextItem(projectId);
        verify(transitions).recordOrphanRun(projectId, itemId, "wf", "run-9");

        when(transitions.recordStarted(projectId, itemId, "run-9", "RUNNING", NOW))
                .thenThrow(new CannotAcquireLockException("busy"));
        step.startNextItem(projectId);
        verify(transitions, org.mockito.Mockito.times(2)).recordOrphanRun(projectId, itemId, "wf", "run-9");
    }

    // ---- RunnerObserveStep

    private final RunOutcomeClassifier classifier = new RunOutcomeClassifier();

    private SpecQueueItem runningItem(String runId) {
        var item = new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "s", null, NOW);
        item.recordRun(runId, NOW);
        return item;
    }

    @Test
    void aRunningItemWithoutARunIdIsFailedAsStateLost() {
        var step = new RunnerObserveStep(transitions, client, classifier, projects);
        var item = runningItem(" ");
        step.observeRunning(item);
        verify(transitions).failActiveItem(projectId, item.getId(), SpecQueueItemStatus.RUNNING,
                SpecQueueFailureReason.RUN_STATE_LOST, null);
        verify(client, never()).getRun(any());
    }

    @Test
    void anUnfinishedRunOnlyRecordsItsNormalizedStatus() {
        var step = new RunnerObserveStep(transitions, client, classifier, projects);
        var item = runningItem("run-1");
        var project = mock(Project.class);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(client.getRun("run-1")).thenReturn(new RunLookup.Found(run("running", null, null)));
        step.observeRunning(item);
        verify(transitions).recordObservation(projectId, item.getId(), "RUNNING");
    }

    // ---- scheduler

    @Test
    void aFailingTickIsSwallowedSoTheSchedulerKeepsRunning() {
        var runner = mock(SpecQueueRunner.class);
        var scheduler = new SpecQueueRunnerScheduler(runner);
        scheduler.tick();
        verify(runner).tick();
        doThrow(new IllegalStateException("boom")).when(runner).tick();
        scheduler.tick();
        verify(runner, org.mockito.Mockito.times(2)).tick();
    }

    // ---- token issuer

    @Test
    void aSecretTooShortToSignIsReportedWithoutEchoingIt() {
        var props = new SpecQueueProperties();
        props.getRunner().getToken().setSecret("short");
        var issuer = new RunnerTokenIssuer(props);
        org.assertj.core.api.Assertions.assertThatThrownBy(issuer::issueToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short");
    }

    // ---- classifier

    private static AgentRunDto run(String status, List<String> artifacts, List<nl.metafactory.aicontrol.client.AgentEventDto> events) {
        return new AgentRunDto("run-1", null, "a.md", null, status, NOW, events, artifacts, "wf", null, null, null);
    }

    @Test
    void everyTerminalStatusMapsToItsFailureReason() {
        assertThat(classifier.classify(run("BLOCKED", null, null), false, null))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.RUN_BLOCKED));
        assertThat(classifier.classify(run("timed_out", null, null), false, null))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.RUN_TIMED_OUT));
        assertThat(classifier.classify(run("RUN_STATE_LOST", null, null), false, null))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.RUN_STATE_LOST));
    }

    @Test
    void aCompletedRunWithOnlyABranchFailsAsPrNotCreated() {
        assertThat(classifier.classify(run("COMPLETED", List.of("git-branch:feature/x"), null), false, null))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.PR_NOT_CREATED));
        assertThat(classifier.classify(run("COMPLETED", List.of("note"), null), false, null))
                .isInstanceOf(RunOutcome.SucceededNoChanges.class);
    }

    // ---- poll error codes

    @Test
    void notSentReasonsMapToFixedPollErrorCodes() {
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.UNCONFIGURED)).isEqualTo(RunnerPollErrorCodes.RUNNER_NOT_CONFIGURED);
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.UNAUTHORIZED)).isEqualTo(RunnerPollErrorCodes.EMBABEL_UNAUTHORIZED);
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.FORBIDDEN)).isEqualTo(RunnerPollErrorCodes.EMBABEL_UNAUTHORIZED);
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.CONNECT_FAILED)).isEqualTo(RunnerPollErrorCodes.EMBABEL_UNAVAILABLE);
    }

    // ---- planner

    private final PlannerItemView view = new PlannerItemView(itemId, "a.md", "wf", 1, true, true);

    @Test
    void fifoHaltsOnACancelAndWaitsOnAnUnfinishedRun() {
        var planner = new FifoQueuePlanner();
        assertThat(planner.onRunFinished(view, new RunOutcome.CancelledByQueue())).isEqualTo(RunFinishedDecision.HALT);
        assertThat(planner.onRunFinished(view, new RunOutcome.NonTerminal("RUNNING"))).isEqualTo(RunFinishedDecision.WAIT_FOR_MERGE);
    }

    @Test
    void guardrailsHaltOnAPlannerCrashAndForceHaltOnCancel() {
        var crashing = mock(nl.metafactory.aicontrol.specqueue.planner.QueuePlanner.class);
        when(crashing.onRunFinished(any(), any())).thenThrow(new IllegalStateException("boom"));
        var decision = PlannerGuardrails.decideAfterRun(crashing, view, new RunOutcome.SucceededNoChanges());
        assertThat(decision.decision()).isEqualTo(RunFinishedDecision.HALT);
        assertThat(decision.override()).contains(PlannerOverride.PLANNER_FAILED);

        var continuing = mock(nl.metafactory.aicontrol.specqueue.planner.QueuePlanner.class);
        when(continuing.onRunFinished(any(), any())).thenReturn(RunFinishedDecision.CONTINUE);
        var forced = PlannerGuardrails.decideAfterRun(continuing, view, new RunOutcome.CancelledByQueue());
        assertThat(forced.decision()).isEqualTo(RunFinishedDecision.HALT);
        assertThat(forced.override()).contains(PlannerOverride.FAILURE_FORCES_HALT);
    }

    @Test
    void guardrailsAreInstantiableForReflectiveFrameworks() {
        assertThat(new PlannerGuardrails()).isNotNull();
    }

    // ---- pull request url

    @Test
    void aPullRequestNumberBeyondIntRangeIsInvalid() {
        var result = PullRequestUrlParser.parse("https://github.com/o/r/pull/9999999999", "https://github.com/o/r.git");
        assertThat(result).isEqualTo(new PullRequestUrlParser.Invalid(SpecQueueFailureReason.PR_URL_INVALID));
    }

    @Test
    void aMissingOrOverlongPullRequestUrlIsInvalid() {
        var invalid = new PullRequestUrlParser.Invalid(SpecQueueFailureReason.PR_URL_INVALID);
        assertThat(PullRequestUrlParser.parse(null, "https://github.com/o/r.git")).isEqualTo(invalid);
        assertThat(PullRequestUrlParser.parse("https://github.com/o/r/pull/" + "1".repeat(600), "https://github.com/o/r.git"))
                .isEqualTo(invalid);
    }

    @Test
    void aCompletedRunWhoseEventsReportAFailedPublicationFails() {
        var failed = new nl.metafactory.aicontrol.client.AgentEventDto(NOW, "git", "publish", " failed ", null);
        var ok = new nl.metafactory.aicontrol.client.AgentEventDto(NOW, "review", "review", "OK", null);
        assertThat(classifier.classify(run("COMPLETED", List.of(), List.of(ok)), false, null))
                .isInstanceOf(RunOutcome.SucceededNoChanges.class);
        assertThat(classifier.classify(run("COMPLETED", List.of(), List.of(failed)), false, null))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.PUBLICATION_FAILED));
    }
}
