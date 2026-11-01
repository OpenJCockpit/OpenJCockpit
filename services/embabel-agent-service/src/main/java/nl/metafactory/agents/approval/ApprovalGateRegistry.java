package nl.metafactory.agents.approval;

import nl.metafactory.agents.orchestration.PipelineState;
import nl.metafactory.agents.orchestration.PipelineStateCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Holds ONLY parked runs (architecture §4.3): a run that never gates never enters it, and a resumed
 * run leaves it. {@link #size()} feeds the {@code approval_gate_paused_runs} gauge.
 *
 * <p><b>Durable</b> (the Spring bean, backed by {@link ParkedRunRepository}): every park is written to
 * {@code agent_parked_runs} as a {@link PipelineStateCodec} snapshot, so a parked run survives a restart
 * ({@link ParkedRunRehydrator} re-adopts it on startup). The database row is the source of truth; the
 * in-memory map is only a cache of the live {@link PipelineState} object, keyed by the row's
 * (iteration, version), so a run parked and decided in the same process keeps its object identity.
 * A cached entry whose (iteration, version) no longer matches the row — e.g. the run was decided and
 * re-parked through another instance — is replaced by a fresh snapshot.</p>
 *
 * <p>Single-owner invariant (ADR-001 "the one honest cost"): {@link #evictIfPresent} removes a parked
 * state before a decision resubmits it to {@code AsyncPipelineRunner}, so two simultaneous decisions
 * can never both resume the same run. Durable: the removal is {@link ParkedRunRepository#claim}, a
 * row-count-checked {@code DELETE}, so this holds across threads and across instances over the same
 * database. In-memory (no-arg constructor, used by unit tests): the map's own per-key {@code compute}.
 * See {@code ApprovalDecisionServiceConcurrencyTest} and {@code DurableApprovalGateRegistryTest}.</p>
 */
@Component
public class ApprovalGateRegistry {

    private record Entry(int iteration, long version, PipelineState state) {
    }

    private final ConcurrentHashMap<String, Entry> parked = new ConcurrentHashMap<>();
    private final ParkedRunRepository store;

    /** In-memory only: parked runs are lost on restart. */
    public ApprovalGateRegistry() {
        this(null);
    }

    @Autowired
    public ApprovalGateRegistry(ParkedRunRepository store) {
        this.store = store;
    }

    public void park(String runId, PipelineState state) {
        int iteration = state.gate() == null ? 0 : state.gate().iteration();
        long version = store == null ? 0
                : store.save(runId, state.gate() == null ? null : state.gate().placementStage(), iteration,
                        PipelineStateCodec.write(state));
        parked.put(runId, new Entry(iteration, version, state));
    }

    /** Startup rehydration: caches a state restored from {@code row} without writing it again. */
    void adopt(ParkedRunRepository.ParkedRun row, PipelineState state) {
        parked.put(row.runId(), new Entry(row.iteration(), row.version(), state));
    }

    public PipelineState get(String runId) {
        Entry entry = current(runId);
        return entry == null ? null : entry.state();
    }

    /** Atomically removes and returns the parked state, or null if none (already resumed/unknown). */
    public PipelineState evict(String runId) {
        return evictIfPresent(runId, state -> state);
    }

    /**
     * Atomic validate-then-remove: applies {@code validate} to the currently parked state (if any) and
     * evicts it ONLY when {@code validate} accepts it (returns non-null). A rejection (e.g. wrong
     * iteration, already decided, not open) leaves the run parked exactly as it was — BR-12/AC-31/AC-32
     * require the gate to remain open at the same iteration when a decision is refused, not to silently
     * vanish from the registry.
     *
     * <p>Two simultaneous callers can never both evict: in-memory, {@link ConcurrentHashMap#compute}
     * serializes them per key; durably, both may validate the same snapshot but only one
     * {@link ParkedRunRepository#claim} deletes the row — the other gets {@code null}, exactly as if it
     * had arrived after the first. {@code validate} must therefore be side-effect free.</p>
     */
    public PipelineState evictIfPresent(String runId, Function<PipelineState, PipelineState> validate) {
        if (store == null) {
            var holder = new PipelineState[1];
            parked.compute(runId, (id, entry) -> {
                if (entry == null) {
                    return null;
                }
                PipelineState accepted = validate.apply(entry.state());
                holder[0] = accepted;
                return accepted != null ? null : entry;
            });
            return holder[0];
        }
        Entry entry = current(runId);
        if (entry == null) {
            return null;
        }
        PipelineState accepted = validate.apply(entry.state());
        if (accepted == null || !store.claim(runId, entry.iteration(), entry.version())) {
            return null;
        }
        parked.remove(runId, entry);
        return accepted;
    }

    public boolean isParked(String runId) {
        return store == null ? parked.containsKey(runId) : store.find(runId).isPresent();
    }

    public int size() {
        return store == null ? parked.size() : store.count();
    }

    /** The live entry for {@code runId}: durable mode reconciles the cache with the row first. */
    private Entry current(String runId) {
        if (store == null) {
            return parked.get(runId);
        }
        var row = store.find(runId).orElse(null);
        if (row == null) {
            parked.remove(runId);
            return null;
        }
        Entry cached = parked.get(runId);
        if (cached != null && cached.iteration() == row.iteration() && cached.version() == row.version()) {
            return cached;
        }
        Entry fresh = new Entry(row.iteration(), row.version(), PipelineStateCodec.read(row.state()));
        parked.put(runId, fresh);
        return fresh;
    }
}
