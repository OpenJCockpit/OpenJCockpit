package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.client.WorkflowGroupDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.FailureCode;
import nl.metafactory.aicontrol.specqueue.runner.RunnerBindingStep.Binding;
import nl.metafactory.aicontrol.specqueue.runner.RunnerGitHubAccessStep.Access;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RunnerStepsUnitTest {

    // ---- lease boundary
    @Test
    void leaseBoundary() {
        Instant claimed = Instant.parse("2026-01-01T00:00:00Z");
        Duration lease = Duration.ofMinutes(5);
        assertThat(SpecQueueRunnerTransitions.isLeaseElapsed(null, lease, claimed)).isTrue();
        assertThat(SpecQueueRunnerTransitions.isLeaseElapsed(claimed, lease, claimed.plus(lease).minusMillis(1))).isFalse();
        assertThat(SpecQueueRunnerTransitions.isLeaseElapsed(claimed, lease, claimed.plus(lease))).isTrue();
    }

    // ---- tick
    private final RunnerIdentityStatus identity = mock(RunnerIdentityStatus.class);
    private final SpecQueueRunnerTransitions transitions = mock(SpecQueueRunnerTransitions.class);
    private final SpecQueueItemRepository items = mock(SpecQueueItemRepository.class);
    private final RunnerStartStep start = mock(RunnerStartStep.class);
    private final RunnerObserveStep observe = mock(RunnerObserveStep.class);
    private final RunnerMergeStep merge = mock(RunnerMergeStep.class);
    private final SpecQueueRunner runner = new SpecQueueRunner(identity, transitions, items, start, observe, merge);

    private static SpecQueueItem item(UUID projectId, SpecQueueItemStatus status) {
        var i = new SpecQueueItem(projectId, "a.md", "wf", null, false, 1, "s", null, Instant.now());
        i.transitionTo(status, Instant.now());
        return i;
    }

    @Test
    void unconfiguredTickTouchesNothing() {
        when(identity.isConfigured()).thenReturn(false);
        runner.tick();
        verify(identity).recordUnconfigured();
        verifyNoInteractions(transitions, items, start, observe, merge);
    }

    @Test
    void dispatchesPerStatusAndStartsNextWhenSlotIsFree() {
        UUID p = UUID.randomUUID();
        when(identity.isConfigured()).thenReturn(true);
        when(transitions.projectIdsWithOpenWork()).thenReturn(List.of(p));
        var running = item(p, SpecQueueItemStatus.RUNNING);
        when(items.findByProjectIdAndActiveSlot(eq(p), any())).thenReturn(Optional.of(running)).thenReturn(Optional.empty());
        runner.tick();
        verify(identity).recordConfigured();
        verify(observe).observeRunning(running);
        verify(start).startNextItem(p);
    }

    @Test
    void activeSlotStaysOccupiedSoNothingStarts() {
        UUID p = UUID.randomUUID();
        when(identity.isConfigured()).thenReturn(true);
        when(transitions.projectIdsWithOpenWork()).thenReturn(List.of(p));
        var merging = item(p, SpecQueueItemStatus.MERGING);
        when(items.findByProjectIdAndActiveSlot(eq(p), any())).thenReturn(Optional.of(merging));
        runner.tick();
        verify(merge).driveMerging(merging);
        verify(start, never()).startNextItem(any());
    }

    @Test
    void unexpectedActiveStatusChangesNothing() {
        UUID p = UUID.randomUUID();
        when(identity.isConfigured()).thenReturn(true);
        when(transitions.projectIdsWithOpenWork()).thenReturn(List.of(p));
        when(items.findByProjectIdAndActiveSlot(eq(p), any())).thenReturn(Optional.of(item(p, SpecQueueItemStatus.QUEUED)));
        runner.tick();
        verifyNoInteractions(observe, merge);
        verify(start, never()).expireStaleStart(any());
    }

    @Test
    void oneFailingOrBusyProjectDoesNotStopOthers() {
        UUID busy = UUID.randomUUID();
        UUID optimistic = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(identity.isConfigured()).thenReturn(true);
        when(transitions.projectIdsWithOpenWork()).thenReturn(List.of(busy, optimistic, broken, fine));
        when(items.findByProjectIdAndActiveSlot(eq(busy), any())).thenThrow(new CannotAcquireLockException("secret-detail"));
        when(items.findByProjectIdAndActiveSlot(eq(optimistic), any())).thenThrow(new OptimisticLockingFailureException("x"));
        when(items.findByProjectIdAndActiveSlot(eq(broken), any())).thenThrow(new IllegalStateException("secret-detail"));
        when(items.findByProjectIdAndActiveSlot(eq(fine), any())).thenReturn(Optional.empty());
        assertThatCode(runner::tick).doesNotThrowAnyException();
        verify(start).startNextItem(fine);
    }

    // ---- scheduler
    @Test
    void schedulerNeverThrows() {
        var runnerMock = mock(SpecQueueRunner.class);
        doThrow(new IllegalStateException("boom")).when(runnerMock).tick();
        assertThatCode(() -> new SpecQueueRunnerScheduler(runnerMock).tick()).doesNotThrowAnyException();
        verify(runnerMock).tick();
    }

    @Test
    void schedulerUsesFixedDelayWithDefault() throws Exception {
        var scheduled = SpecQueueRunnerScheduler.class.getMethod("tick")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(scheduled.fixedDelayString()).isEqualTo("${openjcockpit.spec-queue.runner.poll-interval:PT20S}");
    }

    // ---- identity status
    @SuppressWarnings("unchecked")
    private static ObjectProvider<Clock> clock(Clock c) {
        ObjectProvider<Clock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(c);
        return provider;
    }

    @Test
    void identityStatusConfiguredOnlyWithSecretAndSetsErrorCodeEveryTime() {
        var props = new SpecQueueProperties();
        var clk = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock mutable = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return clk.get(); }
        };
        var status = new RunnerIdentityStatus(props, clock(mutable));
        assertThat(status.isConfigured()).isFalse();
        assertThat(status.isEnabled()).isTrue();
        status.recordUnconfigured();
        status.recordUnconfigured();
        assertThat(status.lastTickErrorCode()).isEqualTo("RUNNER_NOT_CONFIGURED");
        clk.set(clk.get().plus(Duration.ofMinutes(11)));
        status.recordUnconfigured();
        props.getRunner().getToken().setSecret("x".repeat(32));
        assertThat(status.isConfigured()).isTrue();
        status.recordConfigured();
        assertThat(status.lastTickErrorCode()).isNull();
    }

    // ---- binding step
    private static WorkflowDefinitionDto wf(String project, String group, boolean prompt) {
        return new WorkflowDefinitionDto("wf", "WF", project, group, null, null, null, null, null, null, null,
                prompt, null, null, null, null, null, null);
    }

    @Test
    void bindingRules() {
        var client = mock(EmbabelRunnerClient.class);
        var step = new RunnerBindingStep(client);
        when(client.getWorkflow("global")).thenReturn(Optional.of(wf(null, null, false)));
        when(client.getWorkflow("same")).thenReturn(Optional.of(wf("proj", null, false)));
        when(client.getWorkflow("other")).thenReturn(Optional.of(wf("x", null, false)));
        when(client.getWorkflow("missing")).thenReturn(Optional.empty());
        when(client.getWorkflow("prompt")).thenReturn(Optional.of(wf(null, null, true)));
        when(client.getWorkflow("grouped")).thenReturn(Optional.of(wf(null, "g", false)));
        when(client.getWorkflow("grouped-other")).thenReturn(Optional.of(wf(null, "g2", false)));
        when(client.getWorkflow("grouped-global")).thenReturn(Optional.of(wf(null, "g3", false)));
        when(client.getWorkflowGroup("g")).thenReturn(Optional.of(new WorkflowGroupDto("g", "g", null, "proj")));
        when(client.getWorkflowGroup("g2")).thenReturn(Optional.of(new WorkflowGroupDto("g2", "g2", null, "x")));
        when(client.getWorkflowGroup("g3")).thenReturn(Optional.empty());
        when(client.getWorkflow("down")).thenThrow(new UpstreamUnavailableException(FailureCode.TRANSPORT));
        assertThat(step.validate("global", "proj")).isInstanceOf(Binding.Valid.class);
        assertThat(step.validate("same", "proj")).isInstanceOf(Binding.Valid.class);
        assertThat(step.validate("other", "proj")).isInstanceOf(Binding.Invalid.class);
        assertThat(step.validate("missing", "proj")).isInstanceOf(Binding.Invalid.class);
        assertThat(step.validate("prompt", "proj")).isInstanceOf(Binding.Invalid.class);
        assertThat(step.validate("grouped", "proj")).isInstanceOf(Binding.Valid.class);
        assertThat(step.validate("grouped-other", "proj")).isInstanceOf(Binding.Invalid.class);
        assertThat(step.validate("grouped-global", "proj")).isInstanceOf(Binding.Valid.class);
        assertThat(step.validate("down", "proj")).isEqualTo(new Binding.Unavailable(FailureCode.TRANSPORT));
    }

    // ---- access step
    private static Project project(UUID id, String gitUrl) {
        var p = new Project();
        p.setName("proj");
        p.setGitUrl(gitUrl);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static ProjectGitCredential credential(GitCredentialType type, String secret, String api) {
        var c = new ProjectGitCredential();
        c.setCredentialType(type);
        c.setEncryptedSecret(secret);
        c.setGithubApiUrl(api);
        return c;
    }

    @Test
    void accessResolution() {
        UUID id = UUID.randomUUID();
        var repo = mock(ProjectGitCredentialRepository.class);
        var crypto = mock(CredentialEncryptionService.class);
        var step = new RunnerGitHubAccessStep(repo, crypto);
        String url = "https://github.com/acme/repo/pull/3";
        var proj = project(id, "https://github.com/acme/repo.git");

        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, "enc", null)));
        when(crypto.decrypt("enc")).thenReturn("tok");
        var ready = (Access.Ready) step.resolve(proj, url);
        assertThat(ready.apiUrl()).isEqualTo("https://api.github.com");
        assertThat(ready.token()).isEqualTo("tok");
        assertThat(ready.toString()).doesNotContain("tok").doesNotContain("api.github.com");

        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, "enc", "https://ghe.corp/api/v3")));
        assertThat(((Access.Ready) step.resolve(proj, url)).apiUrl()).isEqualTo("https://ghe.corp/api/v3");

        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, "enc", "https://u@x/api")));
        assertThat(step.resolve(proj, url)).isEqualTo(new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));

        clearInvocations(repo);
        assertThat(step.resolve(proj, "https://github.com/acme/other/pull/3"))
                .isEqualTo(new Access.Unusable(SpecQueueFailureReason.PR_URL_INVALID));
        verifyNoInteractions(repo);
        assertThat(step.resolve(project(id, "git@github.com:acme/repo.git"), url))
                .isEqualTo(new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));

        var auth = new Access.Unusable(SpecQueueFailureReason.MERGE_AUTH_FAILED);
        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        assertThat(step.resolve(proj, url)).isEqualTo(auth);
        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.NONE, "enc", null)));
        assertThat(step.resolve(proj, url)).isEqualTo(auth);
        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, null, null)));
        assertThat(step.resolve(proj, url)).isEqualTo(auth);
        when(repo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, "bad", null)));
        when(crypto.decrypt("bad")).thenThrow(new IllegalStateException("x"));
        assertThat(step.resolve(proj, url)).isEqualTo(auth);
        doReturn("  ").when(crypto).decrypt("bad");
        assertThat(step.resolve(proj, url)).isEqualTo(auth);
    }
}
