package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The single-owner invariant under real concurrency (architecture §7.2): two simultaneous ACCEPT
 * submissions against the same parked run must result in exactly one resume and exactly one 409,
 * never both succeeding and never both failing.
 */
class ApprovalDecisionServiceConcurrencyTest {

    @Test
    void twoSimultaneousAcceptsResumeExactlyOnceAndConflictExactlyOnce(@TempDir Path tempDir) throws Exception {
        var runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        var registry = new ApprovalGateRegistry();
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        var auditRepository = new ApprovalDecisionAuditRepository(properties, new YamlDefinitionStore());
        PipelineContinuation continuation = mock(PipelineContinuation.class);
        var service = new ApprovalDecisionService(registry, runStore, auditRepository,
                new ApprovalGateProperties(), continuation);

        String runId = "run-1";
        runStore.create(runId, "cust1", "spec.md", "https://github.com/org/repo", null, null);
        runStore.setStatus(runId, "AWAITING_APPROVAL");
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, new ApprovalGateConfig(true, "realisation"), null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        var state = new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
        var gate = new ApprovalGateState("realisation", true, 3);
        gate.incrementIteration();
        gate.setOpen(true);
        gate.setLastReport(StageChangeReports.notApplicable("x"));
        state.setGate(gate);
        registry.park(runId, state);

        int threadCount = 8;
        var pool = Executors.newFixedThreadPool(threadCount);
        var ready = new CountDownLatch(threadCount);
        var go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    service.submit(runId, new ApprovalDecisionCommand(ApprovalDecisionKind.ACCEPT, 1,
                            null, "user-" + Thread.currentThread().getId(), "sub"));
                    successes.incrementAndGet();
                } catch (org.springframework.web.server.ResponseStatusException e) {
                    conflicts.incrementAndGet();
                }
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threadCount - 1);
        verify(continuation, times(1)).resume(any());
        assertThat(auditRepository.findByRunId(runId)).hasSize(1);
        assertThat(registry.isParked(runId)).isFalse();
    }
}
