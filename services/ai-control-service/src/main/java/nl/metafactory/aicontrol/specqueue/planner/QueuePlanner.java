package nl.metafactory.aicontrol.specqueue.planner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueuePlanner {

    String version();

    Optional<UUID> selectNext(PlannerProjectView project, List<PlannerItemView> queuedInPositionOrder,
                              PlannerHistory history);

    RunFinishedDecision onRunFinished(PlannerItemView item, RunOutcome outcome);
}
