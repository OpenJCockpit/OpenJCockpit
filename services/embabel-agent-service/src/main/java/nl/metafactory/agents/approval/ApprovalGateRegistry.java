package nl.metafactory.agents.approval;

import nl.metafactory.agents.orchestration.PipelineState;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Holds ONLY parked runs (architecture §4.3): a run that never gates never enters this map, and a
 * resumed run leaves it. No cleanup job, no leak, and {@link #size()} is a free gauge for
 * observability ({@code approval_gate_paused_runs}).
 *
 * <p>Single-owner invariant (ADR-001 "the one honest cost"): {@link #evict} is used under the map's
 * own atomicity to remove a parked state before a decision resubmits it to
 * {@code AsyncPipelineRunner}, so two simultaneous decisions can never both resume the same run —
 * see {@code ApprovalDecisionServiceConcurrencyTest} (batch B7).</p>
 */
@Component
public class ApprovalGateRegistry {

    private final ConcurrentHashMap<String, PipelineState> parked = new ConcurrentHashMap<>();

    public void park(String runId, PipelineState state) {
        parked.put(runId, state);
    }

    public PipelineState get(String runId) {
        return parked.get(runId);
    }

    /** Atomically removes and returns the parked state, or null if none (already resumed/unknown). */
    public PipelineState evict(String runId) {
        return parked.remove(runId);
    }

    /**
     * Atomic validate-then-remove: applies {@code validate} to the currently parked state (if any)
     * under the map's own per-key serialization, and evicts it ONLY when {@code validate} accepts
     * it (returns non-null). A rejection (e.g. wrong iteration, already decided, not open) leaves
     * the run parked exactly as it was — BR-12/AC-31/AC-32 require the gate to remain open at the
     * same iteration when a decision is refused, not to silently vanish from the registry.
     *
     * <p>Because {@link ConcurrentHashMap#compute} serializes calls per key, two simultaneous
     * callers can never both see a non-null state and both evict: whichever runs first either
     * accepts (removing the entry) or rejects (leaving it); the second caller then either sees the
     * entry already gone (if the first accepted) or the same still-open state (if the first
     * rejected) — never a torn or duplicated resume.</p>
     */
    public PipelineState evictIfPresent(String runId, Function<PipelineState, PipelineState> validate) {
        var holder = new PipelineState[1];
        parked.compute(runId, (id, state) -> {
            if (state == null) {
                return null;
            }
            PipelineState accepted = validate.apply(state);
            holder[0] = accepted;
            return accepted != null ? null : state;
        });
        return holder[0];
    }

    public boolean isParked(String runId) {
        return parked.containsKey(runId);
    }

    public int size() {
        return parked.size();
    }
}
