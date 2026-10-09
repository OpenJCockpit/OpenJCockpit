package nl.metafactory.aicontrol.specqueue.planner;

import java.util.List;

public record PlannerHistory(List<PlannerHistoryEntry> entries) {

    public PlannerHistory {
        entries = List.copyOf(entries);
    }

    public static PlannerHistory empty() {
        return new PlannerHistory(List.of());
    }
}
