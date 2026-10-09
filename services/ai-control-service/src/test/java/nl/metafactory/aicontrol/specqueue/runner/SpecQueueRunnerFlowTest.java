package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.model.WorkflowPreflightResult;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.GitHubApiException;
import nl.metafactory.aicontrol.service.GitHubPort;
import nl.metafactory.aicontrol.service.GitHubPullRequestState;
import nl.metafactory.aicontrol.service.WorkflowPreflightService;
import nl.metafactory.aicontrol.service.WorkflowStartEnrichmentService;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueTransitions;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.RunLookup;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.StartResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Real database (H2), mocked remote systems: drives whole items through the runner. */
@SpringBootTest
class SpecQueueRunnerFlowTest {

    private static final String GIT_URL = "https://github.com/acme/repo.git";
    private static final String PR_URL = "https://github.com/acme/repo/pull/5";

    @MockitoBean EmbabelRunnerClient embabel;
    @MockitoBean GitHubPort gitHub;
    @MockitoBean WorkflowPreflightService preflight;
    @MockitoBean WorkflowStartEnrichmentService enrichment;

    @Autowired SpecQueueRunner runner;
    @Autowired ProjectRepository projects;
    @Autowired ProjectGitCredentialRepository credentials;
    @Autowired CredentialEncryptionService encryption;
    @Autowired SpecQueueItemRepository items;
    @Autowired SpecQueueRepository queues;
    @Autowired SpecQueueEventRepository events;
    @Autowired SpecQueueTransitions transitions;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    UUID projectId;
    long position;

    @BeforeEach
    void setUp() {
        for (String table : List.of("spec_queue_events", "spec_queue_item_pull_requests", "spec_queue_items",
                "spec_queues", "project_git_credentials")) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from projects");
        Project p = new Project();
        p.setName("proj");
        p.setGitUrl(GIT_URL);
        projectId = projects.save(p).getId();
        var credential = new ProjectGitCredential();
        credential.setProjectId(projectId);
        credential.setCredentialType(GitCredentialType.GITHUB_PAT);
        credential.setEncryptedSecret(encryption.encrypt("ghp_secret_token"));
        credentials.save(credential);
        position = 0;
        reset(embabel, gitHub, preflight, enrichment);
        when(preflight.validateBeforeWorkflowStart(any())).thenReturn(new WorkflowPreflightResult(true, List.of()));
        when(enrichment.enrich(any())).thenAnswer(i -> i.getArgument(0));
        when(embabel.getWorkflow(anyString())).thenReturn(Optional.of(workflow()));
    }

    private static WorkflowDefinitionDto workflow() {
        return new WorkflowDefinitionDto("wf", "WF", null, null, null, null, null, null, null, null, null,
                false, null, null, null, null, null, null);
    }

    private SpecQueueItem item(String spec, boolean autoMerge) {
        transitions.ensureQueueRow(projectId);
        return items.saveAndFlush(new SpecQueueItem(projectId, spec, "wf", null, autoMerge, ++position, "s", null, Instant.now()));
    }

    private void allowAutoMerge() {
        transitions.ensureQueueRow(projectId);
        tx.executeWithoutResult(s -> {
            var q = queues.findById(projectId).orElseThrow();
            q.changeAutoMergeAllowed(true, "test", Instant.now());
            queues.save(q);
        });
    }

    private SpecQueueItem reload(SpecQueueItem item) {
        return items.findById(item.getId()).orElseThrow();
    }

    private SpecQueueState state() {
        return queues.findById(projectId).orElseThrow().getState();
    }

    private void started(String runId) {
        when(embabel.startWorkflow(eq("wf"), any(WorkflowStartInputDto.class)))
                .thenReturn(new StartResult.Started(runId, "RUNNING", Instant.now()));
    }

    private static AgentRunDto run(String status, String... artifacts) {
        return new AgentRunDto("run", null, null, null, status, Instant.parse("2026-01-01T00:00:00Z"), List.of(),
                List.of(artifacts), "wf", "spec-queue-runner", Instant.parse("2026-01-01T00:00:05Z"), "prose");
    }

    private static GitHubPullRequestState pr(boolean open, boolean merged, String mergeableState) {
        return new GitHubPullRequestState(open, merged, false, true, mergeableState, "sha1", "main", 0, 0, 1);
    }

    private List<SpecQueueEventType> eventTypes(SpecQueueItem item) {
        return events.findByItemIdOrderByCreatedAtAsc(item.getId()).stream().map(SpecQueueEvent::getEventType).toList();
    }

    @Test
    void startsOnceThenCompletesWithNoChangesAndStartsTheNextInTheSameTick() {
        var first = item("a.md", false);
        var second = item("b.md", false);
        started("run-1");

        runner.tick();
        assertThat(reload(first).getStatus()).isEqualTo(SpecQueueItemStatus.RUNNING);
        assertThat(reload(first).getWorkflowRunId()).isEqualTo("run-1");
        assertThat(reload(second).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);

        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED")));
        started("run-2");
        runner.tick();

        assertThat(reload(first).getStatus()).isEqualTo(SpecQueueItemStatus.COMPLETED_NO_CHANGES);
        assertThat(reload(second).getStatus()).isEqualTo(SpecQueueItemStatus.RUNNING);
        assertThat(eventTypes(first)).contains(SpecQueueEventType.SELECTED, SpecQueueEventType.STARTED,
                SpecQueueEventType.RUN_FINISHED, SpecQueueEventType.COMPLETED_NO_CHANGES);
        verify(embabel, times(2)).startWorkflow(eq("wf"), any());
        assertThat(state()).isEqualTo(SpecQueueState.ACTIVE);
    }

    @Test
    void autoMergeSquashMergesExactlyOnce() throws Exception {
        allowAutoMerge();
        var item = item("a.md", true);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.MERGING);

        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(true, false, "clean"));
        runner.tick();

        var done = reload(item);
        assertThat(done.getStatus()).isEqualTo(SpecQueueItemStatus.MERGED);
        assertThat(done.getMergeHeadSha()).isEqualTo("sha1");
        verify(gitHub, times(1)).squashMergePullRequest(any(), eq("sha1"), eq("spec-queue: a.md"), anyString(), eq("ghp_secret_token"));
        assertThat(eventTypes(item)).contains(SpecQueueEventType.MERGE_ATTEMPTED, SpecQueueEventType.MERGED);
        runner.tick();
        verify(gitHub, times(1)).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void autoMergeThatNeverBecomesMergeableFailsAfterTheWaitTimeout() throws Exception {
        allowAutoMerge();
        var item = item("a.md", true);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(true, false, "unknown"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.MERGING);

        jdbc.update("update spec_queue_items set updated_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(java.time.Duration.ofHours(2))), item.getId());
        runner.tick();

        var failed = reload(item);
        assertThat(failed.getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(SpecQueueFailureReason.MERGE_WAIT_TIMEOUT);
        assertThat(state()).isEqualTo(SpecQueueState.HALTED);
        verify(gitHub, never()).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void autoMergeNotPermittedByProjectWaitsForHumanThenCompletes() throws Exception {
        var item = item("a.md", true); // project setting stays off
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);

        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(true, false, "clean"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);

        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(false, true, "clean"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.MERGED);
        verify(gitHub, never()).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void closedUnmergedPullRequestFailsAndHalts() throws Exception {
        var item = item("a.md", false);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(false, false, "clean"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.PR_CLOSED_UNMERGED);
        assertThat(state()).isEqualTo(SpecQueueState.HALTED);
    }

    @Test
    void failedRunHaltsQueueAndNextItemIsNotStarted() {
        var first = item("a.md", false);
        var second = item("b.md", false);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("FAILED")));
        runner.tick();
        assertThat(reload(first).getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(reload(first).getFailureReason()).isEqualTo(SpecQueueFailureReason.RUN_FAILED);
        assertThat(reload(second).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        assertThat(state()).isEqualTo(SpecQueueState.HALTED);
        runner.tick();
        verify(embabel, times(1)).startWorkflow(any(), any());
    }

    @Test
    void ambiguousStartFailsWithoutRetry() {
        var item = item("a.md", false);
        when(embabel.startWorkflow(any(), any())).thenReturn(new StartResult.Ambiguous(EmbabelRunnerClient.AmbiguousReason.TIMEOUT));
        runner.tick();
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.START_OUTCOME_UNKNOWN);
        assertThat(state()).isEqualTo(SpecQueueState.HALTED);
        verify(embabel, times(1)).startWorkflow(any(), any());
    }

    @Test
    void rejectedStartRecordsStartRejectedEvent() {
        var item = item("a.md", false);
        when(embabel.startWorkflow(any(), any())).thenReturn(new StartResult.Rejected(400, null));
        runner.tick();
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.START_REJECTED);
        assertThat(eventTypes(item)).contains(SpecQueueEventType.START_REJECTED);
        assertThat(events.findByProjectIdOrderByCreatedAtDesc(projectId, org.springframework.data.domain.Pageable.unpaged()))
                .extracting(SpecQueueEvent::getEventType).contains(SpecQueueEventType.HALTED);
    }

    @Test
    void notSentRevertsToQueuedWithPollError() {
        var item = item("a.md", false);
        when(embabel.startWorkflow(any(), any())).thenReturn(new StartResult.NotSent(EmbabelRunnerClient.NotSentReason.CONNECT_FAILED));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        assertThat(reload(item).getStartClaimedAt()).isNull();
        assertThat(queues.findById(projectId).orElseThrow().getLastPollErrorCode()).isEqualTo("EMBABEL_UNAVAILABLE");
        assertThat(state()).isEqualTo(SpecQueueState.ACTIVE);
        assertThat(eventTypes(item)).contains(SpecQueueEventType.START_REVERTED);
    }

    @Test
    void failedPreflightFailsWithoutStarting() {
        var item = item("a.md", false);
        when(preflight.validateBeforeWorkflowStart(any())).thenReturn(new WorkflowPreflightResult(false, List.of()));
        runner.tick();
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.PREFLIGHT_FAILED);
        verify(embabel, never()).startWorkflow(any(), any());
    }

    @Test
    void preflightRuntimeExceptionRevertsInsteadOfHalting() {
        var item = item("a.md", false);
        when(preflight.validateBeforeWorkflowStart(any())).thenThrow(new IllegalStateException("git unreachable"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        assertThat(queues.findById(projectId).orElseThrow().getLastPollErrorCode()).isEqualTo("PREFLIGHT_UNAVAILABLE");
        assertThat(state()).isEqualTo(SpecQueueState.ACTIVE);
        verify(embabel, never()).startWorkflow(any(), any());
    }

    @Test
    void embabelOutageOnlyRecordsPollError() {
        var item = item("a.md", false);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Unavailable(EmbabelRunnerClient.FailureCode.NOT_FOUND));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.RUNNING);
        assertThat(queues.findById(projectId).orElseThrow().getLastPollErrorCode()).isEqualTo("EMBABEL_UNAVAILABLE");
    }

    @Test
    void staleStartingIsFailedAsOutcomeUnknownButFreshOneIsLeftAlone() {
        var item = item("a.md", false);
        tx.executeWithoutResult(s -> {
            var i = reload(item);
            i.transitionTo(SpecQueueItemStatus.STARTING, Instant.now());
            i.recordStartClaim(Instant.now());
            items.saveAndFlush(i);
        });
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.STARTING);
        tx.executeWithoutResult(s -> {
            var i = reload(item);
            i.recordStartClaim(Instant.now().minusSeconds(3600));
            items.saveAndFlush(i);
        });
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.START_OUTCOME_UNKNOWN);
        verify(embabel, never()).startWorkflow(any(), any());
    }

    @Test
    void staleStartingWithOrphanRunSurfacesTheOrphanRunId() {
        var item = item("a.md", false);
        tx.executeWithoutResult(s -> {
            var i = reload(item);
            i.transitionTo(SpecQueueItemStatus.STARTING, Instant.now());
            i.recordStartClaim(Instant.now().minusSeconds(3600));
            items.saveAndFlush(i);
            events.save(new nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent(projectId, i.getId(),
                    SpecQueueEventType.ORPHAN_RUN_DETECTED, "SYSTEM", Instant.now())
                    .withWorkflow(i.getWorkflowId(), "orphan-run"));
        });
        runner.tick();
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.START_OUTCOME_UNKNOWN);
        assertThat(reload(item).getWorkflowRunId()).isEqualTo("orphan-run");
    }

    @Test
    void expiredMergeAttemptIsDecidedByOneReadAndNeverMergedAgain() throws Exception {
        allowAutoMerge();
        var item = item("a.md", true);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        tx.executeWithoutResult(s -> {
            var i = reload(item);
            i.recordMergeAttempt(Instant.now().minusSeconds(3600), "sha1");
            items.saveAndFlush(i);
        });
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(true, false, "clean"));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.MERGE_OUTCOME_UNKNOWN);
        verify(gitHub, times(1)).readPullRequest(any(), anyString(), anyString());
        verify(gitHub, never()).squashMergePullRequest(any(), any(), any(), any(), any());
    }

    @Test
    void refusedMergeOnUnmergedPullRequestIsBlockedAfterOneRead() throws Exception {
        allowAutoMerge();
        var item = item("a.md", true);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenReturn(pr(true, false, "clean"));
        doThrow(new GitHubApiException(409, false)).when(gitHub).squashMergePullRequest(any(), any(), any(), any(), any());
        runner.tick();
        assertThat(reload(item).getFailureReason()).isEqualTo(SpecQueueFailureReason.MERGE_BLOCKED);
        verify(gitHub, times(1)).squashMergePullRequest(any(), any(), any(), any(), any());
        verify(gitHub, times(2)).readPullRequest(any(), anyString(), anyString());
    }

    @Test
    void githubOutageOnlyRecordsPollError() throws Exception {
        var item = item("a.md", false);
        started("run-1");
        runner.tick();
        when(embabel.getRun("run-1")).thenReturn(new RunLookup.Found(run("COMPLETED", "pull-request:" + PR_URL)));
        runner.tick();
        when(gitHub.readPullRequest(any(), anyString(), anyString())).thenThrow(new GitHubApiException(429, true));
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.AWAITING_MERGE);
        assertThat(queues.findById(projectId).orElseThrow().getLastPollErrorCode()).isEqualTo("GITHUB_RATE_LIMITED");
    }

    @Test
    void pausedQueueStartsNothingAndPlannerNoSelectionIsRecordedOnce() {
        var item = item("a.md", false);
        tx.executeWithoutResult(s -> {
            var q = queues.findById(projectId).orElseThrow();
            q.changeState(SpecQueueState.PAUSED, Instant.now());
            queues.save(q);
        });
        runner.tick();
        assertThat(reload(item).getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
        verify(embabel, never()).startWorkflow(any(), any());
    }
}
