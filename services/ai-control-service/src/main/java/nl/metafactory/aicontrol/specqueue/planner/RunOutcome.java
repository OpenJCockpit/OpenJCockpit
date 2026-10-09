package nl.metafactory.aicontrol.specqueue.planner;

import nl.metafactory.aicontrol.model.SpecQueueFailureReason;

import java.util.List;

public sealed interface RunOutcome {

    default List<RunPullRequest> pullRequests() {
        return List.of();
    }

    record NonTerminal(String runStatus) implements RunOutcome {}

    record Failed(SpecQueueFailureReason reason) implements RunOutcome {}

    record CancelledByQueue() implements RunOutcome {}

    record SucceededNoChanges() implements RunOutcome {}

    record SucceededWithPr(RunPullRequest pullRequest) implements RunOutcome {
        @Override
        public List<RunPullRequest> pullRequests() {
            return List.of(pullRequest);
        }
    }

    record SucceededWithPrs(List<RunPullRequest> pullRequests) implements RunOutcome {
        public SucceededWithPrs {
            if (pullRequests == null || pullRequests.size() < 2) {
                throw new IllegalArgumentException("SucceededWithPrs requires at least two pull requests");
            }
            pullRequests = List.copyOf(pullRequests);
        }
    }
}
