package nl.metafactory.aicontrol.specqueue.github;

import nl.metafactory.aicontrol.service.GitHubPullRequestState;

import java.util.Locale;
import java.util.Objects;

public final class MergeabilityEvaluator {

    private MergeabilityEvaluator() {
    }

    public static MergeDecision evaluate(GitHubPullRequestState state) {
        Objects.requireNonNull(state, "state");
        if (state.merged()) return MergeDecision.ALREADY_MERGED;
        if (!state.open()) return MergeDecision.CLOSED_UNMERGED;
        if (state.draft()) return MergeDecision.BLOCKED;
        String ms = state.mergeableState() == null ? null : state.mergeableState().toLowerCase(Locale.ROOT);
        if (state.mergeable() == null || ms == null || ms.equals("unknown")) return MergeDecision.WAIT;
        return switch (ms) {
            case "dirty" -> MergeDecision.CONFLICT;
            case "clean", "has_hooks" -> MergeDecision.MERGE;
            case "blocked" -> state.checksPending() > 0 ? MergeDecision.WAIT : MergeDecision.BLOCKED;
            case "unstable" -> state.checksPending() > 0 ? MergeDecision.WAIT
                    : state.checksFailing() > 0 ? MergeDecision.BLOCKED : MergeDecision.WAIT;
            default -> MergeDecision.BLOCKED;
        };
    }
}
