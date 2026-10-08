package nl.metafactory.agents.spec;

import java.util.List;

/**
 * Which realisation attempt this is (architecture §7.4). Iteration 1 is the original run;
 * iteration ≥2 is a feedback loop-back (BR-17…BR-24, realisation placement only). Carries exactly
 * what the re-run needs: the operator's comment, the previous attempt's changed paths (so the
 * agent knows what it already produced), and the retained branch/PR so the same branch is reused
 * and a repeat pull-request call can be skipped (BR-20/BR-22).
 */
public record RealisationIteration(
        int number,
        String feedback,
        List<String> previousChangedPaths,
        String retainedBranch,
        String retainedPullRequestUrl
) {

    public static RealisationIteration first() {
        return new RealisationIteration(1, null, List.of(), null, null);
    }

    public boolean isFirst() {
        return number <= 1;
    }
}
