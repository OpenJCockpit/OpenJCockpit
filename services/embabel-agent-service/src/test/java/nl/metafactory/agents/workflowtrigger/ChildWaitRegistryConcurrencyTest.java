package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChildWaitRegistryConcurrencyTest {

    private final ChildWaitRegistry registry = new ChildWaitRegistry();

    private PipelineState samplePipelineState(String runId) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, null, null, null, null,
                RunInitiator.trigger("test"), List.of("wf-test"));
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""),
                Set.of("realisation"), request);
    }

    @SuppressWarnings("unchecked")
    private ChildWait sampleWait(String parentRunId, String childRunId) {
        ScheduledFuture<Object> deadline = mock(ScheduledFuture.class);
        return new ChildWait(samplePipelineState(parentRunId), childRunId, "wf-child", "Child Workflow",
                "impact", Instant.now(), deadline);
    }

    @Test
    void exactlyOneOfManyConcurrentClaimersWinsForTheSameParkedParent() throws Exception {
        registry.park("parent-race", sampleWait("parent-race", "child-race"));

        int threadCount = 8;
        var barrier = new CyclicBarrier(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);
        var nonNullResults = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            boolean useClaimByChild = i % 2 == 0;
            executor.submit(() -> {
                try {
                    barrier.await();
                    ChildWait result = useClaimByChild
                            ? registry.claimByChild("child-race")
                            : registry.claim("parent-race");
                    if (result != null) {
                        nonNullResults.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(nonNullResults.get()).isEqualTo(1);
        assertThat(registry.size()).isZero();
    }
}
