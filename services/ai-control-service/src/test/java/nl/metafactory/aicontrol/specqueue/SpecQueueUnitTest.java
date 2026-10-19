package nl.metafactory.aicontrol.specqueue;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueMergeState;
import nl.metafactory.aicontrol.model.SpecQueuePollErrorDto;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.specqueue.app.CurrentActor;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueDtoMapper;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEvent;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueEventType;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecQueueUnitTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private final UUID projectId = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private SpecQueueItem item() {
        return new SpecQueueItem(projectId, "a.md", "wf", "WF", false, 1, "sub", "alice", T0);
    }

    // ── domain ──────────────────────────────────────────────────────────────

    @Test
    void constructorWithSizeStoresItAndOldConstructorLeavesItNull() {
        var sized = new SpecQueueItem(projectId, "a.md", "wf", "WF", false, 1, 42, "sub", "alice", T0);
        assertThat(sized.getSpecFileSizeBytes()).isEqualTo(42);
        assertThat(item().getSpecFileSizeBytes()).isNull();
    }

    @Test
    void itemDerivesActiveSlotAndOpenKeyFromStatus() {
        SpecQueueItem i = item();
        assertThat(i.getActiveSlot()).isNull();
        assertThat(i.getOpenSpecKey()).isEqualTo("a.md");
        i.transitionTo(SpecQueueItemStatus.RUNNING, T1);
        assertThat(i.getActiveSlot()).isEqualTo((short) 1);
        i.transitionTo(SpecQueueItemStatus.FAILED, T1);
        assertThat(i.getActiveSlot()).isNull();
        assertThat(i.getOpenSpecKey()).isEqualTo("a.md");
        i.transitionTo(SpecQueueItemStatus.MERGED, T1);
        assertThat(i.getOpenSpecKey()).isNull();
    }

    @Test
    void reassignKeepsOpenKeyInStepWithTheFile() {
        SpecQueueItem i = item();
        i.reassign("b.md", "wf2", "WF2", true, 42, T1);
        assertThat(i.getOpenSpecKey()).isEqualTo("b.md");
        assertThat(i.getSpecFileSizeBytes()).isEqualTo(42);
        assertThat(i.isAutoMerge()).isTrue();
        assertThat(i.getWorkflowId()).isEqualTo("wf2");
    }

    @Test
    void itemRecordsRunnerBookkeeping() {
        SpecQueueItem i = item();
        i.moveTo(7, T1);
        i.recordFailureReason(SpecQueueFailureReason.RUN_FAILED, T1);
        i.recordStartClaim(T1);
        i.recordRun("run-1", T1);
        i.recordLastRunStatus("RUNNING", T1);
        i.recordMergeAttempt(T1, "abc");
        i.recordCancelRequest(T1);
        i.recordFinished(T1);
        assertThat(i.getPosition()).isEqualTo(7);
        assertThat(i.getFailureReason()).isEqualTo(SpecQueueFailureReason.RUN_FAILED);
        assertThat(i.getStartClaimedAt()).isEqualTo(T1);
        assertThat(i.getWorkflowRunId()).isEqualTo("run-1");
        assertThat(i.getStartedAt()).isEqualTo(T1);
        assertThat(i.getLastRunStatus()).isEqualTo("RUNNING");
        assertThat(i.getMergeAttemptStartedAt()).isEqualTo(T1);
        assertThat(i.getMergeHeadSha()).isEqualTo("abc");
        assertThat(i.getCancelRequestedAt()).isEqualTo(T1);
        assertThat(i.getFinishedAt()).isEqualTo(T1);
        assertThat(i.getUpdatedAt()).isEqualTo(T1);
        assertThat(i.getCreatedBySub()).isEqualTo("sub");
        assertThat(i.getCreatedByUsername()).isEqualTo("alice");
        assertThat(i.getCreatedAt()).isEqualTo(T0);
        assertThat(i.getProjectId()).isEqualTo(projectId);
        assertThat(i.getVersion()).isNull();

        i.recordStartClaim(null);
        i.recordRun(null, null);
        i.recordMergeAttempt(null, null);
        i.recordCancelRequest(null);
        i.recordFinished(null);
        assertThat(i.getStartClaimedAt()).isNull();
        assertThat(i.getWorkflowRunId()).isNull();
        assertThat(i.getMergeAttemptStartedAt()).isNull();
        assertThat(i.getCancelRequestedAt()).isNull();
        assertThat(i.getFinishedAt()).isNull();
    }

    @Test
    void queueRecordsStateSettingsAndPolling() {
        SpecQueue q = new SpecQueue(projectId, T0);
        assertThat(q.getState()).isEqualTo(SpecQueueState.ACTIVE);
        assertThat(q.isAutoMergeAllowed()).isFalse();
        q.changeState(SpecQueueState.HALTED, T1);
        q.changeAutoMergeAllowed(true, "USER:x", T1);
        q.recordPollError("EMBABEL_UNAVAILABLE", T1);
        q.rememberPlannerDecision("NEXT", T1);
        assertThat(q.getState()).isEqualTo(SpecQueueState.HALTED);
        assertThat(q.getSettingsUpdatedBy()).isEqualTo("USER:x");
        assertThat(q.getSettingsUpdatedAt()).isEqualTo(T1);
        assertThat(q.getLastPollErrorCode()).isEqualTo("EMBABEL_UNAVAILABLE");
        assertThat(q.getLastPollErrorAt()).isEqualTo(T1);
        assertThat(q.getLastPlannerDecision()).isEqualTo("NEXT");
        q.recordPollSuccess(T1.plusSeconds(1));
        assertThat(q.getLastPolledAt()).isEqualTo(T1.plusSeconds(1));
        assertThat(q.getLastPollErrorCode()).isNull();
        assertThat(q.getCreatedAt()).isEqualTo(T0);
        assertThat(q.getUpdatedAt()).isEqualTo(T1.plusSeconds(1));
        assertThat(q.getVersion()).isNull();
        assertThat(q.getProjectId()).isEqualTo(projectId);
    }

    @Test
    void eventCollectsEveryFacet() {
        UUID itemId = UUID.randomUUID();
        SpecQueueEvent e = new SpecQueueEvent(projectId, itemId, SpecQueueEventType.FAILED, "RUNNER", T0)
                .withStatusChange(SpecQueueItemStatus.RUNNING, SpecQueueItemStatus.FAILED)
                .withReasonCode("RUN_FAILED")
                .withWorkflow("wf", "run")
                .withPlanner("NEXT", "v1", "none")
                .withRunMetrics(5L, 2, 1, "APPROVED")
                .withPublication("MERGED", 10, 3);
        assertThat(e.getId()).isNotNull();
        assertThat(e.getProjectId()).isEqualTo(projectId);
        assertThat(e.getItemId()).isEqualTo(itemId);
        assertThat(e.getEventType()).isEqualTo(SpecQueueEventType.FAILED);
        assertThat(e.getActor()).isEqualTo("RUNNER");
        assertThat(e.getFromStatus()).isEqualTo(SpecQueueItemStatus.RUNNING);
        assertThat(e.getToStatus()).isEqualTo(SpecQueueItemStatus.FAILED);
        assertThat(e.getReasonCode()).isEqualTo("RUN_FAILED");
        assertThat(e.getWorkflowId()).isEqualTo("wf");
        assertThat(e.getWorkflowRunId()).isEqualTo("run");
        assertThat(e.getPlannerDecision()).isEqualTo("NEXT");
        assertThat(e.getPlannerVersion()).isEqualTo("v1");
        assertThat(e.getPlannerOverride()).isEqualTo("none");
        assertThat(e.getRunDurationMs()).isEqualTo(5L);
        assertThat(e.getReviewIterations()).isEqualTo(2);
        assertThat(e.getApprovalGateIterations()).isEqualTo(1);
        assertThat(e.getApprovalGateOutcome()).isEqualTo("APPROVED");
        assertThat(e.getMergeResult()).isEqualTo("MERGED");
        assertThat(e.getSpecFileSizeBytes()).isEqualTo(10);
        assertThat(e.getPrCount()).isEqualTo(3);
        assertThat(e.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void pullRequestCanBeMarkedMergedOrClosed() {
        UUID itemId = UUID.randomUUID();
        var pr = new SpecQueueItemPullRequest(itemId, "run", "https://github.com/o/r/pull/3", "o", "r", 3, T0);
        pr.markMerged(T1);
        pr.markClosedUnmerged(T1);
        assertThat(pr.getId()).isNotNull();
        assertThat(pr.getItemId()).isEqualTo(itemId);
        assertThat(pr.getWorkflowRunId()).isEqualTo("run");
        assertThat(pr.getUrl()).endsWith("/3");
        assertThat(pr.getOwner()).isEqualTo("o");
        assertThat(pr.getRepo()).isEqualTo("r");
        assertThat(pr.getNumber()).isEqualTo(3);
        assertThat(pr.getMergedAt()).isEqualTo(T1);
        assertThat(pr.getClosedUnmergedAt()).isEqualTo(T1);
        assertThat(pr.getCreatedAt()).isEqualTo(T0);
    }

    // ── mapper ──────────────────────────────────────────────────────────────

    @Test
    void mapperDerivesMergeStateCreatedByAndPollError() {
        var props = new SpecQueueProperties();
        props.getRunner().getToken().setSecret("x".repeat(32));
        var mapper = new SpecQueueDtoMapper(props);

        SpecQueueItem i = item();
        assertThat(mapper.toItemDto(i, 1, List.of()).mergeState()).isNull();
        i.transitionTo(SpecQueueItemStatus.MERGING, T1);
        assertThat(mapper.toItemDto(i, null, List.of("u")).mergeState()).isEqualTo(SpecQueueMergeState.WAITING_FOR_MERGEABILITY);
        i.recordMergeAttempt(T1, "sha");
        var dto = mapper.toItemDto(i, null, List.of("u"));
        assertThat(dto.mergeState()).isEqualTo(SpecQueueMergeState.MERGE_IN_PROGRESS);
        assertThat(dto.createdBy()).isEqualTo("alice");
        assertThat(dto.pullRequestUrls()).containsExactly("u");
        var noName = new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "sub-9", null, T0);
        assertThat(mapper.toItemDto(noName, 1, List.of()).createdBy()).isEqualTo("sub-9");

        SpecQueue q = new SpecQueue(projectId, T0);
        q.recordPollError("GITHUB_UNAVAILABLE", T1);
        var queueDto = mapper.toQueueDto(projectId, q, List.of(), List.of());
        assertThat(queueDto.lastPollError()).isEqualTo(new SpecQueuePollErrorDto("GITHUB_UNAVAILABLE", T1));
        assertThat(queueDto.runner().configured()).isTrue();
        assertThat(mapper.toQueueDto(projectId, new SpecQueue(projectId, T0), List.of(), List.of()).lastPollError()).isNull();
        var transientQueue = mapper.toQueueDto(projectId, null, List.of(), List.of());
        assertThat(transientQueue.state()).isEqualTo(SpecQueueState.ACTIVE);
        assertThat(transientQueue.autoMergeAllowed()).isFalse();
        assertThat(mapper.toSettingsDto(null).updatedAt()).isNull();
        assertThat(mapper.toSettingsDto(q).autoMergeAllowed()).isFalse();
    }

    // ── properties accessors ────────────────────────────────────────────────

    @Test
    void propertiesSettersChangeValues() {
        var p = new SpecQueueProperties();
        var r = new SpecQueueProperties.Runner();
        var t = new SpecQueueProperties.Token();
        t.setIssuer("i");
        t.setAudience("a");
        t.setTtl(Duration.ofSeconds(5));
        t.setSecret("s");
        r.setEnabled(false);
        r.setPollInterval(Duration.ofSeconds(1));
        r.setStartLease(Duration.ofSeconds(2));
        r.setMergeLease(Duration.ofSeconds(3));
        r.setEmbabelTimeout(Duration.ofSeconds(4));
        r.setGithubTimeout(Duration.ofSeconds(5));
        r.setRecentlyFinishedLimit(3);
        r.setMaxQueuedItems(4);
        r.setToken(t);
        p.setRunner(r);
        assertThat(p.getRunner().isEnabled()).isFalse();
        assertThat(r.getPollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(r.getStartLease()).isEqualTo(Duration.ofSeconds(2));
        assertThat(r.getMergeLease()).isEqualTo(Duration.ofSeconds(3));
        assertThat(r.getEmbabelTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(r.getGithubTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(r.getRecentlyFinishedLimit()).isEqualTo(3);
        assertThat(r.getMaxQueuedItems()).isEqualTo(4);
        assertThat(t.getIssuer()).isEqualTo("i");
        assertThat(t.getAudience()).isEqualTo("a");
        assertThat(t.getTtl()).isEqualTo(Duration.ofSeconds(5));
        assertThat(t.getSecret()).isEqualTo("s");
    }

    // ── current actor ───────────────────────────────────────────────────────

    private static Jwt jwt(String sub, String username) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").claim("k", "v")
                .issuedAt(T0).expiresAt(T0.plusSeconds(60));
        if (sub != null) {
            b.subject(sub);
        }
        if (username != null) {
            b.claim("preferred_username", username);
        }
        return b.build();
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void longSubjectsThatShareAPrefixGetDistinctLabels() {
        String prefix = "s".repeat(90);
        authenticate(jwt(prefix + "a", null));
        CurrentActor first = CurrentActor.fromSecurityContext();
        authenticate(jwt(prefix + "b", null));
        CurrentActor second = CurrentActor.fromSecurityContext();
        assertThat(first.subject()).hasSize(70).isNotEqualTo(second.subject());
        assertThat(first.eventLabel()).hasSize(75);
        authenticate(jwt(prefix + "a", null));
        assertThat(CurrentActor.fromSecurityContext().subject()).isEqualTo(first.subject());
    }

    @Test
    void currentActorReadsAndTruncatesJwtClaims() {
        authenticate(jwt("s".repeat(100), "n".repeat(300)));
        CurrentActor actor = CurrentActor.fromSecurityContext();
        assertThat(actor.subject()).hasSize(70);
        assertThat(actor.displayName()).hasSize(255);
        assertThat(actor.eventLabel()).hasSize(75).startsWith("USER:");

        authenticate(jwt("sub-1", null));
        assertThat(CurrentActor.fromSecurityContext().displayName()).isNull();
    }

    @Test
    void currentActorRejectsMissingOrUnusableAuthentication() {
        assertThatThrownBy(CurrentActor::fromSecurityContext).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pw"));
        assertThatThrownBy(CurrentActor::fromSecurityContext).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        authenticate(jwt(" ", null));
        assertThatThrownBy(CurrentActor::fromSecurityContext).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        authenticate(jwt(null, null));
        assertThatThrownBy(CurrentActor::fromSecurityContext).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    // ── exception matrix ────────────────────────────────────────────────────

    @Test
    void everyCodeHasAStatus() {
        for (Code code : Code.values()) {
            assertThat(new SpecQueueException(code, "m").getHttpStatus()).isNotNull();
        }
        assertThat(new SpecQueueException(Code.QUEUE_BUSY, "m").getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(new SpecQueueException(Code.EMBABEL_UNAVAILABLE, "m").getCode()).isEqualTo(Code.EMBABEL_UNAVAILABLE);
    }

    // ── workflow binding validator ──────────────────────────────────────────

    private static WorkflowDefinitionDto wf(String name, String project, String group, boolean prompt) {
        return new WorkflowDefinitionDto("wf", name, project, group, null, List.of(), List.of(), List.of(),
                List.of(), null, null, prompt, null, null, null, null, null, null);
    }

    private static Code codeOf(Runnable r) {
        return ((SpecQueueException) catchThrowable(r)).getCode();
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    void validatorAppliesBindingRules() {
        var client = mock(EmbabelAgentClient.class);
        var validator = new WorkflowBindingValidator(client);

        when(client.getWorkflowStrict("same")).thenReturn(Optional.of(wf("N", "proj", null, false)));
        assertThat(validator.validate("same", "proj").workflowName()).isEqualTo("N");

        when(client.getWorkflowStrict("global")).thenReturn(Optional.of(wf(null, null, " ", false)));
        assertThat(validator.validate("global", "proj").workflowName()).isNull();

        when(client.getWorkflowStrict("long")).thenReturn(Optional.of(wf("x".repeat(300), "", null, false)));
        assertThat(validator.validate("long", "proj").workflowName()).hasSize(255);

        when(client.getWorkflowStrict("other")).thenReturn(Optional.of(wf("N", "Proj", null, false)));
        assertThat(codeOf(() -> validator.validate("other", "proj"))).isEqualTo(Code.WORKFLOW_NOT_ALLOWED_FOR_PROJECT);

        when(client.getWorkflowStrict("cross-prompt")).thenReturn(Optional.of(wf("N", "other", null, true)));
        assertThat(codeOf(() -> validator.validate("cross-prompt", "proj"))).isEqualTo(Code.WORKFLOW_NOT_ALLOWED_FOR_PROJECT);

        when(client.getWorkflowStrict("prompt")).thenReturn(Optional.of(wf("N", null, null, true)));
        assertThat(codeOf(() -> validator.validate("prompt", "proj"))).isEqualTo(Code.WORKFLOW_PROMPT_REQUIRED);

        when(client.getWorkflowStrict("missing")).thenReturn(Optional.empty());
        assertThat(codeOf(() -> validator.validate("missing", "proj"))).isEqualTo(Code.WORKFLOW_NOT_FOUND);

        when(client.getWorkflowStrict("down")).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        assertThat(codeOf(() -> validator.validate("down", "proj"))).isEqualTo(Code.EMBABEL_UNAVAILABLE);
    }

    @Test
    void validatorFallsBackToGroupProject() {
        var client = mock(EmbabelAgentClient.class);
        var validator = new WorkflowBindingValidator(client);
        when(client.getWorkflowStrict("g1")).thenReturn(Optional.of(wf("N", null, "grp", false)));

        when(client.getWorkflowGroupStrict("grp")).thenReturn(Optional.of(new WorkflowGroupDto("grp", "G", null, "proj")));
        assertThat(validator.validate("g1", "proj").workflowId()).isEqualTo("g1");
        assertThat(codeOf(() -> validator.validate("g1", "else"))).isEqualTo(Code.WORKFLOW_NOT_ALLOWED_FOR_PROJECT);

        when(client.getWorkflowGroupStrict("grp")).thenReturn(Optional.of(new WorkflowGroupDto("grp", "G", null, " ")));
        assertThat(validator.validate("g1", "else").workflowId()).isEqualTo("g1");
        when(client.getWorkflowGroupStrict("grp")).thenReturn(Optional.empty());
        assertThat(validator.validate("g1", "else").workflowId()).isEqualTo("g1");

        when(client.getWorkflowGroupStrict("grp")).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        assertThat(codeOf(() -> validator.validate("g1", "proj"))).isEqualTo(Code.EMBABEL_UNAVAILABLE);
        assertThat(Arrays.asList(Code.values())).contains(Code.EMBABEL_UNAVAILABLE);
    }
}
