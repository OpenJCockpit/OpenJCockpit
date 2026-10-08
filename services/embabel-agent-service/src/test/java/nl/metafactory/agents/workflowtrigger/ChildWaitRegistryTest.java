package nl.metafactory.agents.workflowtrigger;

import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChildWaitRegistryTest {

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
    void parkThenClaimReturnsTheSameWaitAndSecondClaimReturnsNull() {
        var wait = sampleWait("parent-1", "child-1");
        registry.park("parent-1", wait);

        assertThat(registry.claim("parent-1")).isEqualTo(wait);
        assertThat(registry.claim("parent-1")).isNull();
    }

    @Test
    void claimOnUnparkedParentReturnsNull() {
        assertThat(registry.claim("no-such-parent")).isNull();
    }

    @Test
    void parkThenClaimByChildReturnsTheSameWaitAndSubsequentDirectClaimReturnsNull() {
        var wait = sampleWait("parent-2", "child-2");
        registry.park("parent-2", wait);

        assertThat(registry.claimByChild("child-2")).isEqualTo(wait);
        assertThat(registry.claim("parent-2")).isNull();
    }

    @Test
    void claimByChildForUnknownChildReturnsNull() {
        assertThat(registry.claimByChild("no-such-child")).isNull();
    }

    @Test
    void sizeReflectsParkedEntryCountBeforeAndAfterClaim() {
        assertThat(registry.size()).isZero();

        var wait = sampleWait("parent-3", "child-3");
        registry.park("parent-3", wait);
        assertThat(registry.size()).isEqualTo(1);

        registry.claim("parent-3");
        assertThat(registry.size()).isZero();
    }
}
