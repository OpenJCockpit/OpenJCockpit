package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.gitworkspace.GitWorkspaceBinding;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.AgentRunStatuses;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.AsyncPipelineRunner;
import nl.metafactory.agents.orchestration.EmbabelOrchestrator;
import nl.metafactory.agents.orchestration.InMemoryAgentRunOutputs;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.PipelineStateCodec;
import nl.metafactory.agents.persistence.AgentRunReconciliationService;
import nl.metafactory.agents.persistence.JpaAgentRunPersistence;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import nl.metafactory.agents.workflow.agents.AgentRef;
import nl.metafactory.agents.workflow.agents.AgentWorkflowProperties;
import nl.metafactory.agents.workflow.agents.WorkflowAgentInvoker;
import nl.metafactory.agents.workflowrun.WorkflowRunScope;
import nl.metafactory.agents.workflowrun.WorkflowRunScopes;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A run paused at an approval gate survives a restart of embabel-agent-service. A "restart" is a brand-new
 * set of {@link AgentRunStore}, {@link ApprovalGateRegistry}, {@link WorkflowRunScopes}, orchestrator and
 * decision service over the SAME H2 database (real Flyway migrations, real JPA/JDBC persistence), after
 * the real startup sequence: {@link AgentRunReconciliationService} then {@link ParkedRunRehydrator}. Only
 * the agent invoker (or the continuation) is mocked. Not wrapped in a test transaction, so every
 * "instance" sees committed rows only — exactly as separate processes would.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import({JpaAgentRunPersistence.class, ParkedRunRepository.class, AgentRunReconciliationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParkedRunRestartIntegrationTest {

    private static final String ANALYST = "local:analyst";
    private static final String DEVELOPER = "a2a:litellm:developer";
    private static final String QA = "a2a:litellm:qa";
    private static final GitWorkspaceBinding WORKSPACE =
            new GitWorkspaceBinding("ws-1", "https://example.invalid/repo.git", "feature/run");

    @Autowired JpaAgentRunPersistence persistence;
    @Autowired ParkedRunRepository parkedRuns;
    @Autowired AgentRunReconciliationService reconciler;
    @Autowired JdbcClient jdbc;

    @TempDir Path auditDir;
    private ApprovalDecisionAuditRepository auditRepository;
    private final ApprovalGateProperties gateProperties = new ApprovalGateProperties();

    /** Everything one process holds in memory. */
    private final class Instance {
        final AgentRunStore runStore = new AgentRunStore(persistence, event -> { });
        final ApprovalGateRegistry registry = new ApprovalGateRegistry(parkedRuns);
        final WorkflowRunScopes scopes = new WorkflowRunScopes();
        final WorkflowAgentInvoker invoker = mock(WorkflowAgentInvoker.class);
        final EmbabelOrchestrator orchestrator;
        final ApprovalDecisionService decisions;

        Instance(PipelineContinuation continuation) {
            when(invoker.invoke(any(), anyString(), any())).thenAnswer(inv -> "output of " + inv.getArgument(0));
            orchestrator = new EmbabelOrchestrator(new AsyncPipelineRunner(), runStore,
                    new ApprovalGateCoordinator(registry, runStore, gateProperties), registry,
                    mock(WorkflowOrbRunner.class), mock(WorkflowTriggerCoordinator.class), invoker,
                    new AgentWorkflowProperties(), new InMemoryAgentRunOutputs(), scopes);
            decisions = new ApprovalDecisionService(registry, runStore, auditRepository, gateProperties,
                    continuation != null ? continuation : orchestrator);
        }

        Instance() {
            this(null);
        }

        /** The startup sequence of a fresh process (both are ApplicationRunners, in this order). */
        Instance started() {
            reconciler.run(null);
            new ParkedRunRehydrator(parkedRuns, registry, runStore, scopes).run(null);
            return this;
        }

        void verifyInvoked(String key, int count) {
            verify(invoker, times(count)).invoke(eq(AgentRef.parse(key).orElseThrow()), anyString(), any());
        }
    }

    @BeforeEach
    void setUp() {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(auditDir.toString());
        auditRepository = new ApprovalDecisionAuditRepository(properties, new YamlDefinitionStore());
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM agent_runs").update();
    }

    private static AgentRunRequest gatedAfterDeveloper() {
        return new AgentRunRequest("cust1", "the spec", List.of(ANALYST, DEVELOPER, QA), "workflow:wf-1", "", null,
                "token-never-persisted", new ApprovalGateConfig(true, DEVELOPER), null, "wf-1", "alice",
                RunInitiator.human("alice", "sub-1"), List.of("wf-1"));
    }

    private static ApprovalDecisionCommand decision(ApprovalDecisionKind kind, int iteration, String comment) {
        return new ApprovalDecisionCommand(kind, iteration, comment, "alice", "sub-1");
    }

    private String durableStatus(String runId) {
        return persistence.findFullRun(runId).orElseThrow().status();
    }

    @Test
    void aParkedRunSurvivesARestartAndIsResumedExactlyOnceWhileInFlightRunsAreReconciledLost() {
        var before = new Instance();
        String runId = before.orchestrator.start(gatedAfterDeveloper(), WORKSPACE).runId();
        before.runStore.create("run-in-flight", "cust1", "spec", "", "wf-1", "alice");
        assertThat(durableStatus(runId)).isEqualTo("AWAITING_APPROVAL");
        assertThat(jdbc.sql("SELECT state FROM agent_parked_runs").query(String.class).single())
                .doesNotContain("token-never-persisted");

        var after = new Instance().started();

        assertThat(durableStatus("run-in-flight")).isEqualTo(AgentRunStatuses.RUN_STATE_LOST);
        assertThat(after.runStore.get("run-in-flight")).isNull();
        assertThat(after.orchestrator.get(runId).status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(after.registry.size()).isEqualTo(1);
        var gate = after.registry.get(runId).gate();
        assertThat(gate.isOpen()).isTrue();
        assertThat(gate.iteration()).isEqualTo(1);
        var scope = new WorkflowRunScope("wf-1", runId, WORKSPACE);
        assertThat(after.scopes.find(runId)).contains(scope);

        // Feedback re-runs the developer with its pre-restart output, then parks again (iteration 2).
        after.decisions.submit(runId, decision(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 1, "add tests"));
        var message = ArgumentCaptor.forClass(String.class);
        var invocation = ArgumentCaptor.forClass(WorkflowAgentInvoker.Invocation.class);
        verify(after.invoker).invoke(eq(AgentRef.parse(DEVELOPER).orElseThrow()), message.capture(), invocation.capture());
        assertThat(message.getValue()).contains("add tests").contains("output of " + AgentRef.parse(DEVELOPER).orElseThrow());
        assertThat(invocation.getValue().workflow()).isEqualTo(scope);
        assertThat(durableStatus(runId)).isEqualTo("AWAITING_APPROVAL");
        assertThat(parkedRuns.find(runId).orElseThrow().iteration()).isEqualTo(2);

        after.decisions.submit(runId, decision(ApprovalDecisionKind.ACCEPT, 2, null));
        assertThatThrownBy(() -> after.decisions.submit(runId, decision(ApprovalDecisionKind.ACCEPT, 2, null)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(durableStatus(runId)).isEqualTo("COMPLETED");
        assertThat(parkedRuns.count()).isZero();
        after.verifyInvoked(ANALYST, 0);
        after.verifyInvoked(DEVELOPER, 1);
        after.verifyInvoked(QA, 1);
        before.verifyInvoked(QA, 0);
        assertThat(after.scopes.find(runId)).isEmpty();
        assertThat(auditRepository.findByRunId(runId)).hasSize(2);
    }

    @Test
    void concurrentDecisionsOnTwoRestartedInstancesResumeTheRunExactlyOnce() throws Exception {
        String runId = new Instance().orchestrator.start(gatedAfterDeveloper()).runId();
        var continuationB = mock(PipelineContinuation.class);
        var continuationC = mock(PipelineContinuation.class);
        var instances = List.of(new Instance(continuationB).started(), new Instance(continuationC).started());

        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var instance = instances.get(i % 2);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    instance.decisions.submit(runId, decision(ApprovalDecisionKind.ACCEPT, 1, null));
                    successes.incrementAndGet();
                } catch (ResponseStatusException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (var f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        var resumedB = mockingDetailsCount(continuationB);
        var resumedC = mockingDetailsCount(continuationC);
        assertThat(resumedB + resumedC).isEqualTo(1);
        assertThat(parkedRuns.count()).isZero();
        assertThat(auditRepository.findByRunId(runId)).hasSize(1);
    }

    private static long mockingDetailsCount(PipelineContinuation continuation) {
        return org.mockito.Mockito.mockingDetails(continuation).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("resume")).count();
    }

    @Test
    void aSnapshotThatCannotBeRestoredFailsTheRunWithAPersistedReasonInsteadOfHanging() {
        var before = new Instance();
        before.runStore.create("run-corrupt", "cust1", "spec", "", "wf-1", "alice");
        before.runStore.create("run-foreign", "cust1", "spec", "", "wf-1", "alice");
        parkedRuns.save("run-corrupt", DEVELOPER, 1, "{not json");
        parkedRuns.save("run-foreign", DEVELOPER, 1,
                PipelineStateCodec.write(DurableApprovalGateRegistryTest.gatedState("some-other-run", 1)));

        var after = new Instance().started();

        for (String runId : List.of("run-corrupt", "run-foreign")) {
            var run = persistence.findFullRun(runId).orElseThrow();
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.failureSummary()).startsWith(ParkedRunRehydrator.RESTORE_FAILED);
            assertThat(run.events()).extracting(e -> e.title()).contains(ParkedRunRehydrator.RESTORE_FAILED);
            assertThat(after.registry.isParked(runId)).isFalse();
        }
        assertThatThrownBy(() -> after.decisions.submit("run-corrupt", decision(ApprovalDecisionKind.ACCEPT, 1, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aParkedRowOfAnAlreadyFinishedRunIsDroppedOnStartup() {
        var before = new Instance();
        before.runStore.create("run-finished", "cust1", "spec", "", "wf-1", "alice");
        before.runStore.setStatus("run-finished", "COMPLETED");
        jdbc.sql("""
                        INSERT INTO agent_parked_runs (run_id, gate_stage, iteration, state, parked_at, version)
                        VALUES ('run-finished', 'x', 1, '{}', :at, 0)""")
                .param("at", OffsetDateTime.now()).update();

        var after = new Instance().started();

        assertThat(parkedRuns.count()).isZero();
        assertThat(durableStatus("run-finished")).isEqualTo("COMPLETED");
        assertThat(after.runStore.get("run-finished")).isNull();
        verify(after.invoker, never()).invoke(any(), anyString(), any());
    }
}
