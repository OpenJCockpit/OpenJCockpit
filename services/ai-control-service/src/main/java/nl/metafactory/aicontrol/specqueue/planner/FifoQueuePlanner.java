package nl.metafactory.aicontrol.specqueue.planner;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FifoQueuePlanner implements QueuePlanner {

    public static final String VERSION = "FIFO-v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Optional<UUID> selectNext(PlannerProjectView project, List<PlannerItemView> queued,
                                     PlannerHistory history) {
        return queued.stream().min(Comparator.comparingInt(PlannerItemView::position)).map(PlannerItemView::id);
    }

    @Override
    public RunFinishedDecision onRunFinished(PlannerItemView item, RunOutcome outcome) {
        return switch (outcome) {
            case RunOutcome.Failed f -> RunFinishedDecision.HALT;
            case RunOutcome.CancelledByQueue c -> RunFinishedDecision.HALT;
            case RunOutcome.SucceededNoChanges n -> RunFinishedDecision.CONTINUE;
            case RunOutcome.SucceededWithPr p -> item.effectiveAutoMerge()
                    ? RunFinishedDecision.MERGE_AND_CONTINUE : RunFinishedDecision.WAIT_FOR_MERGE;
            case RunOutcome.SucceededWithPrs p -> RunFinishedDecision.WAIT_FOR_MERGE;
            case RunOutcome.NonTerminal n -> RunFinishedDecision.WAIT_FOR_MERGE;
        };
    }
}
