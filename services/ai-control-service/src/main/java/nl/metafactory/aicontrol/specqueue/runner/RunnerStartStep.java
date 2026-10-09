package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import nl.metafactory.aicontrol.service.WorkflowStartEnrichmentService;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.StartResult;
import nl.metafactory.aicontrol.specqueue.runner.SpecQueueRunnerTransitions.StartClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Starts the next item exactly once. A start whose outcome is unknown is failed, never retried. */
@Component
public class RunnerStartStep {

    private static final Logger log = LoggerFactory.getLogger(RunnerStartStep.class);

    private final SpecQueueRunnerTransitions transitions;
    private final RunnerBindingStep bindingStep;
    private final EmbabelRunnerClient client;
    private final WorkflowPreflightService preflightService;
    private final WorkflowStartEnrichmentService enrichmentService;
    private final ProjectRepository projects;
    private final SpecQueueProperties.Runner config;
    private final Clock clock;

    public RunnerStartStep(SpecQueueRunnerTransitions transitions, RunnerBindingStep bindingStep,
                           EmbabelRunnerClient client, WorkflowPreflightService preflightService,
                           WorkflowStartEnrichmentService enrichmentService, ProjectRepository projects,
                           SpecQueueProperties properties, ObjectProvider<Clock> clock) {
        this.transitions = transitions;
        this.bindingStep = bindingStep;
        this.client = client;
        this.preflightService = preflightService;
        this.enrichmentService = enrichmentService;
        this.projects = projects;
        this.config = properties.getRunner();
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public void startNextItem(java.util.UUID projectId) {
        var claimed = transitions.claimNextItem(projectId);
        if (claimed.isEmpty()) {
            return;
        }
        StartClaim claim = claimed.get();
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project of a claimed item is missing"));

        var binding = bindingStep.validate(claim.workflowId(), project.getName());
        if (binding instanceof RunnerBindingStep.Binding.Invalid) {
            transitions.failStart(projectId, claim.itemId(), SpecQueueFailureReason.WORKFLOW_INVALID, SpecQueueEventType.FAILED);
            return;
        }
        if (binding instanceof RunnerBindingStep.Binding.Unavailable unavailable) {
            transitions.revertStart(projectId, claim.itemId(), RunnerPollErrorCodes.forEmbabel(unavailable.code()));
            return;
        }

        WorkflowStartInputDto input;
        try {
            if (!preflightService.validateBeforeWorkflowStart(projectId).passed()) {
                transitions.failStart(projectId, claim.itemId(), SpecQueueFailureReason.PREFLIGHT_FAILED, SpecQueueEventType.FAILED);
                return;
            }
            input = enrichmentService.enrich(new WorkflowStartInputDto(null, claim.specFile(), null,
                    projectId.toString(), null, null, null));
        } catch (RuntimeException e) {
            transitions.failStart(projectId, claim.itemId(), SpecQueueFailureReason.PREFLIGHT_FAILED, SpecQueueEventType.FAILED);
            return;
        }

        StartResult result = client.startWorkflow(claim.workflowId(), input);
        switch (result) {
            case StartResult.Started started -> recordStarted(claim, started);
            case StartResult.Rejected rejected -> transitions.failStart(projectId, claim.itemId(),
                    SpecQueueFailureReason.START_REJECTED, SpecQueueEventType.START_REJECTED);
            case StartResult.NotSent notSent -> transitions.revertStart(projectId, claim.itemId(),
                    RunnerPollErrorCodes.forNotSent(notSent.reason()));
            case StartResult.Ambiguous ambiguous -> transitions.failStart(projectId, claim.itemId(),
                    SpecQueueFailureReason.START_OUTCOME_UNKNOWN, SpecQueueEventType.FAILED);
        }
    }

    private void recordStarted(StartClaim claim, StartResult.Started started) {
        boolean recorded;
        try {
            recorded = transitions.recordStarted(claim.projectId(), claim.itemId(), started.runId(),
                    started.status(), started.startedAt());
        } catch (PessimisticLockingFailureException | OptimisticLockingFailureException e) {
            recorded = false;
        }
        if (!recorded) {
            // The run exists but could not be bound to the item. The runner holds no stop privilege.
            log.warn("spec-queue.orphan-run projectId={} itemId={} runId={}", claim.projectId(), claim.itemId(), started.runId());
            transitions.recordOrphanRun(claim.projectId(), claim.itemId(), claim.workflowId(), started.runId());
        }
    }

    /** A start whose lease is still running belongs to a start in flight and is left alone. */
    public void expireStaleStart(SpecQueueItem item) {
        if (SpecQueueRunnerTransitions.isLeaseElapsed(item.getStartClaimedAt(), config.getStartLease(), clock.instant())) {
            transitions.expireStartIfLeaseElapsed(item.getProjectId(), item.getId());
        }
    }
}
