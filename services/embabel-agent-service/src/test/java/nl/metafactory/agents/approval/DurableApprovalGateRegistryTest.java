package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApprovalGateRegistry} backed by {@link ParkedRunRepository} over a real H2 database with the
 * real Flyway migrations. Not wrapped in a test transaction: the registries here must see each other's
 * committed rows, exactly like two processes (or one process before and after a restart) would.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@Import(ParkedRunRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DurableApprovalGateRegistryTest {

    private static final String RUN = "run-durable-1";

    @Autowired JdbcClient jdbc;
    @Autowired ParkedRunRepository store;

    @BeforeEach
    void insertRun() {
        jdbc.sql("INSERT INTO agent_runs (run_id, status, started_at) VALUES (:run, 'RUNNING', :at)")
                .param("run", RUN).param("at", OffsetDateTime.now()).update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM agent_runs").update();
    }

    static PipelineState gatedState(String runId, int iterations) {
        var request = new AgentRunRequest("cust1", "spec", List.of("local:dev"), "workflow:wf-1", "", null, null,
                new ApprovalGateConfig(true, "local:dev"), null, "wf-1", "alice", RunInitiator.trigger("test"), List.of());
        var state = new PipelineState(runId, new SpecContent(runId, "spec.md", "spec", ""), Set.of("local:dev"), request);
        var gate = new ApprovalGateState("local:dev", true, 3);
        for (int i = 0; i < iterations; i++) {
            gate.incrementIteration();
        }
        gate.setOpen(true);
        state.setGate(gate);
        return state;
    }

    private String durableStatus() {
        return jdbc.sql("SELECT status FROM agent_runs WHERE run_id = :run").param("run", RUN).query(String.class).single();
    }

    @Test
    void parkWritesTheRowAndAwaitingStatusAndAFreshRegistrySeesTheSameGate() {
        var registry = new ApprovalGateRegistry(store);
        var state = gatedState(RUN, 1);

        registry.park(RUN, state);

        assertThat(registry.get(RUN)).isSameAs(state);
        assertThat(durableStatus()).isEqualTo("AWAITING_APPROVAL");
        var afterRestart = new ApprovalGateRegistry(store);
        assertThat(afterRestart.isParked(RUN)).isTrue();
        assertThat(afterRestart.size()).isEqualTo(1);
        var restored = afterRestart.get(RUN);
        assertThat(restored).isNotSameAs(state);
        assertThat(restored.gate().iteration()).isEqualTo(1);
        assertThat(restored.gate().isOpen()).isTrue();
        assertThat(afterRestart.get(RUN)).as("cached after the first read").isSameAs(restored);
    }

    @Test
    void parkingAgainReplacesTheRowWithANewVersion() {
        var registry = new ApprovalGateRegistry(store);
        registry.park(RUN, gatedState(RUN, 1));
        registry.park(RUN, gatedState(RUN, 2));

        var row = store.find(RUN).orElseThrow();
        assertThat(row.version()).isEqualTo(1);
        assertThat(row.iteration()).isEqualTo(2);
        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void aRejectedDecisionLeavesTheRunParkedAndAnAcceptedOneClaimsIt() {
        var registry = new ApprovalGateRegistry(store);
        var state = gatedState(RUN, 1);
        registry.park(RUN, state);

        assertThat(registry.evictIfPresent(RUN, s -> null)).isNull();
        assertThat(registry.isParked(RUN)).isTrue();

        assertThat(registry.evictIfPresent(RUN, s -> s)).isSameAs(state);
        assertThat(registry.isParked(RUN)).isFalse();
        assertThat(registry.size()).isZero();
        assertThat(durableStatus()).isEqualTo("RUNNING");
        assertThat(registry.evict(RUN)).isNull();
        assertThat(registry.get(RUN)).isNull();
    }

    @Test
    void aStaleCachedGateIsNeverDecidedAfterAnotherInstanceDecidedAndReParkedTheRun() {
        var instanceA = new ApprovalGateRegistry(store);
        var instanceB = new ApprovalGateRegistry(store);
        instanceA.park(RUN, gatedState(RUN, 1));
        assertThat(instanceB.get(RUN).gate().iteration()).isEqualTo(1);

        // A decides iteration 1 and the re-run parks iteration 2 (a brand-new row, version 0 again).
        instanceA.evict(RUN);
        instanceA.park(RUN, gatedState(RUN, 2));

        assertThat(instanceB.get(RUN).gate().iteration()).isEqualTo(2);
        assertThat(instanceB.evictIfPresent(RUN, s -> s.gate().iteration() == 1 ? s : null)).isNull();
        assertThat(instanceB.isParked(RUN)).isTrue();
    }

    @Test
    void concurrentDecisionsAcrossTwoInstancesClaimTheRunExactlyOnce() throws Exception {
        new ApprovalGateRegistry(store).park(RUN, gatedState(RUN, 1));
        var instances = List.of(new ApprovalGateRegistry(store), new ApprovalGateRegistry(store));

        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        var wins = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var registry = instances.get(i % 2);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                if (registry.evictIfPresent(RUN, s -> s) != null) {
                    wins.incrementAndGet();
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

        assertThat(wins.get()).isEqualTo(1);
        assertThat(store.count()).isZero();
    }

    @Test
    void aStateWithoutAGateCanBeParked() {
        var registry = new ApprovalGateRegistry(store);
        var request = new AgentRunRequest("cust1", "spec", List.of("local:dev"), "u", "", null, null, null, null,
                null, null, RunInitiator.trigger("test"), List.of());
        registry.park(RUN, new PipelineState(RUN, new SpecContent(RUN, "s", "s", ""), Set.of("local:dev"), request));

        assertThat(store.find(RUN).orElseThrow().iteration()).isZero();
        assertThat(new ApprovalGateRegistry(store).get(RUN).gate()).isNull();
    }
}
