package nl.metafactory.agents.approval;

import nl.metafactory.agents.domain.SpecContent;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.orchestration.PipelineState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGateRegistryTest {

    private PipelineState state(String runId) {
        var request = new AgentRunRequest("cust1", "spec.md", List.of("realisation"), "workflow:wf-1",
                "https://github.com/org/repo", null, null, null, null, null,
                null, nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        return new PipelineState(runId, new SpecContent(runId, "spec.md", "spec.md", ""), Set.of("realisation"), request);
    }

    @Test
    void parkThenGetReturnsTheSameState() {
        var registry = new ApprovalGateRegistry();
        var state = state("run-1");

        registry.park("run-1", state);

        assertThat(registry.get("run-1")).isSameAs(state);
        assertThat(registry.isParked("run-1")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void getReturnsNullForARunThatWasNeverParked() {
        var registry = new ApprovalGateRegistry();

        assertThat(registry.get("missing")).isNull();
        assertThat(registry.isParked("missing")).isFalse();
    }

    @Test
    void evictRemovesAndReturnsTheParkedState() {
        var registry = new ApprovalGateRegistry();
        var state = state("run-1");
        registry.park("run-1", state);

        var evicted = registry.evict("run-1");

        assertThat(evicted).isSameAs(state);
        assertThat(registry.isParked("run-1")).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void evictOnAnUnparkedRunReturnsNull() {
        var registry = new ApprovalGateRegistry();

        assertThat(registry.evict("missing")).isNull();
    }

    @Test
    void evictIfPresentValidatesAndAtomicallyRemovesTheParkedRun() {
        var registry = new ApprovalGateRegistry();
        var state = state("run-1");
        registry.park("run-1", state);

        var result = registry.evictIfPresent("run-1", s -> s);

        assertThat(result).isSameAs(state);
        assertThat(registry.isParked("run-1")).isFalse();
    }

    @Test
    void evictIfPresentReturnsNullWhenNothingIsParked() {
        var registry = new ApprovalGateRegistry();

        var result = registry.evictIfPresent("missing", s -> s);

        assertThat(result).isNull();
    }

    @Test
    void evictIfPresentLeavesTheEntryParkedWhenTheValidatorRejectsIt() {
        var registry = new ApprovalGateRegistry();
        var state = state("run-1");
        registry.park("run-1", state);

        // BR-12/AC-31/AC-32: a rejected decision (e.g. wrong iteration, already decided) must
        // leave the gate open at the same iteration, not silently evict it from the registry.
        var result = registry.evictIfPresent("run-1", s -> null);

        assertThat(result).isNull();
        assertThat(registry.isParked("run-1")).isTrue();
        assertThat(registry.get("run-1")).isSameAs(state);
    }

    @Test
    void sizeReflectsOnlyCurrentlyParkedRuns() {
        var registry = new ApprovalGateRegistry();
        registry.park("run-1", state("run-1"));
        registry.park("run-2", state("run-2"));

        assertThat(registry.size()).isEqualTo(2);

        registry.evict("run-1");

        assertThat(registry.size()).isEqualTo(1);
    }
}
