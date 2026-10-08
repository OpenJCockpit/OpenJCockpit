package nl.metafactory.agents.workflowtrigger;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/** Holds currently parked SEQUENTIAL child waits, keyed by parent run id, with a reverse index by child run id. */
@Component
public class ChildWaitRegistry {

    private final ConcurrentHashMap<String, ChildWait> parked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> childToParent = new ConcurrentHashMap<>();

    /** Parks wait under parentRunId, writing both the forward map and the reverse index. */
    public void park(String parentRunId, ChildWait wait) {
        parked.put(parentRunId, wait);
        childToParent.put(wait.childRunId(), parentRunId);
    }

    /** Atomically removes and returns the parked wait for parentRunId, or null if none is parked. */
    public ChildWait claim(String parentRunId) {
        ChildWait[] holder = new ChildWait[1];
        parked.compute(parentRunId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            holder[0] = existing;
            childToParent.remove(existing.childRunId(), parentRunId);
            return null;
        });
        return holder[0];
    }

    /** Looks up the reverse index for childRunId, then delegates to claim(parentRunId). Returns null if unknown. */
    public ChildWait claimByChild(String childRunId) {
        String parentRunId = childToParent.get(childRunId);
        if (parentRunId == null) {
            return null;
        }
        return claim(parentRunId);
    }

    /** Returns the number of currently parked entries. */
    public int size() {
        return parked.size();
    }
}
