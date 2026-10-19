package nl.metafactory.aicontrol.specqueue.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The only way the runner talks to a planner. Guardrails only downgrade, never upgrade. */
public class PlannerGuardrails {

    public record Selection(Optional<UUID> itemId, Optional<PlannerOverride> override) {}

    public record Decision(RunFinishedDecision decision, Optional<PlannerOverride> override) {}

    public static Selection selectNext(QueuePlanner planner, PlannerProjectView project,
                                       List<PlannerItemView> queued, PlannerHistory history) {
        Optional<UUID> chosen;
        try {
            chosen = planner.selectNext(project, new ArrayList<>(queued), history);
        } catch (RuntimeException e) {
            return none(PlannerOverride.PLANNER_FAILED);
        }
        if (chosen == null) {
            return none(PlannerOverride.INVALID_SELECTION);
        }
        if (chosen.isEmpty()) {
            return new Selection(Optional.empty(), Optional.empty());
        }
        UUID id = chosen.get();
        if (queued.stream().noneMatch(i -> i.id().equals(id))) {
            return none(PlannerOverride.INVALID_SELECTION);
        }
        return new Selection(chosen, Optional.empty());
    }

    public static Decision decideAfterRun(QueuePlanner planner, PlannerItemView item, RunOutcome outcome) {
        RunFinishedDecision d;
        try {
            d = planner.onRunFinished(item, outcome);
        } catch (RuntimeException e) {
            return of(RunFinishedDecision.HALT, PlannerOverride.PLANNER_FAILED);
        }
        if (d == null) {
            return of(RunFinishedDecision.HALT, PlannerOverride.NULL_DECISION);
        }
        return switch (outcome) {
            case RunOutcome.NonTerminal n -> of(RunFinishedDecision.HALT, PlannerOverride.UNEXPECTED_OUTCOME);
            case RunOutcome.Failed f -> forcedHalt(d);
            case RunOutcome.CancelledByQueue c -> forcedHalt(d);
            case RunOutcome.SucceededNoChanges n -> switch (d) {
                case HALT -> plain(d);
                case CONTINUE -> plain(d);
                case WAIT_FOR_MERGE, MERGE_AND_CONTINUE ->
                        of(RunFinishedDecision.CONTINUE, PlannerOverride.NO_PULL_REQUESTS);
            };
            case RunOutcome.SucceededWithPr p -> withPrs(d, item.effectiveAutoMerge());
            case RunOutcome.SucceededWithPrs p -> withPrs(d, false);
        };
    }

    private static Decision withPrs(RunFinishedDecision d, boolean mergePermitted) {
        return switch (d) {
            case HALT, WAIT_FOR_MERGE -> plain(d);
            case CONTINUE -> of(RunFinishedDecision.WAIT_FOR_MERGE, PlannerOverride.PULL_REQUESTS_PRESENT);
            case MERGE_AND_CONTINUE -> mergePermitted ? plain(d)
                    : of(RunFinishedDecision.WAIT_FOR_MERGE, PlannerOverride.MERGE_NOT_PERMITTED);
        };
    }

    private static Decision forcedHalt(RunFinishedDecision d) {
        return d == RunFinishedDecision.HALT ? plain(d)
                : of(RunFinishedDecision.HALT, PlannerOverride.FAILURE_FORCES_HALT);
    }

    private static Selection none(PlannerOverride o) {
        return new Selection(Optional.empty(), Optional.of(o));
    }

    private static Decision plain(RunFinishedDecision d) {
        return new Decision(d, Optional.empty());
    }

    private static Decision of(RunFinishedDecision d, PlannerOverride o) {
        return new Decision(d, Optional.of(o));
    }
}
