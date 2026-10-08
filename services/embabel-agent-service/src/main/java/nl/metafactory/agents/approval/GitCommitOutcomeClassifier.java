package nl.metafactory.agents.approval;

/**
 * Contains F2's fragility (architecture ADR-006) in exactly one place: {@code git_commit} reports
 * "nothing to commit" only as a message string, with no error code on {@code GitToolResult}. See
 * {@code services/git-mcp-server/src/main/java/nl/metafactory/gitmcp/git/GitToolsService.java:108}
 * — {@code GitToolResult.failure("Nothing to commit: workspace has no uncommitted changes")}.
 * {@code git-mcp-server} is NOT modified — no error code is added, no MCP contract change.
 *
 * <p>A misclassification here is cosmetic, not functional: under BR-40, {@code NO_CHANGE} and
 * {@code PUBLISH_FAILED} differ only in the reason string shown to the operator; both still open
 * the same gate, keep the run non-terminal, and offer the same decisions.</p>
 */
public final class GitCommitOutcomeClassifier {

    // Pinned literal — see the GitToolsService.java:108 citation above. If git-mcp-server's
    // wording ever changes, GitCommitOutcomeClassifierTest fails loudly here, in one place.
    static final String NOTHING_TO_COMMIT_MESSAGE = "Nothing to commit: workspace has no uncommitted changes";

    private GitCommitOutcomeClassifier() {
    }

    public static boolean isNothingToCommit(String message) {
        return NOTHING_TO_COMMIT_MESSAGE.equals(message);
    }
}
